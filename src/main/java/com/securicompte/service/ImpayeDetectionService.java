package com.securicompte.service;

import com.securicompte.entity.Client;
import com.securicompte.entity.Impaye;
import com.securicompte.entity.Souscription;
import com.securicompte.enums.StatutImpaye;
import com.securicompte.repository.ClientRepository;
import com.securicompte.repository.ImpayeRepository;
import com.securicompte.repository.SouscriptionRepository;
import com.securicompte.repository.StockMensuelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service de détection automatique des impayés.
 *
 * ALGORITHME :
 * Pour chaque client ayant souscrit avant ou pendant le mois M :
 *   - S'il EST dans le stock du mois M  → PAYÉ (aucun impayé créé)
 *   - S'il N'EST PAS dans le stock du mois M → IMPAYÉ enregistré
 *
 * Si le client réapparaît un mois suivant :
 *   - Les impayés précédents passent en REGULARISE
 *   - L'historique est conservé
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImpayeDetectionService {

    private final ClientRepository        clientRepository;
    private final SouscriptionRepository  souscriptionRepository;
    private final StockMensuelRepository  stockMensuelRepository;
    private final ImpayeRepository        impayeRepository;

    /**
     * Détecte les impayés pour le mois/année donné.
     * Appelé automatiquement après chaque import.
     *
     * @return nombre d'impayés nouvellement créés
     */
    @Transactional
    public int detecterImpaYesDuMois(Integer annee, Integer mois) {
        log.info("=== DÉTECTION IMPAYÉS {}/{} ===", mois, annee);

        // 1. Clients présents dans le stock ce mois (= ont payé)
        List<Long> clientsPayesIds = stockMensuelRepository
            .findClientIdsPresentsDansMois(annee, mois);
        log.info("Clients présents dans le stock : {}", clientsPayesIds.size());

        // 2. Parcourir tous les clients
        List<Client> tousClients = clientRepository.findAll();
        int nbCreés = 0;

        for (Client client : tousClients) {
            // Le client doit avoir souscrit avant ou pendant ce mois
            if (!avaitSouscritAvant(client.getId(), annee, mois)) continue;

            boolean aPayé = clientsPayesIds.contains(client.getId());

            if (aPayé) {
                // Présent → régulariser les éventuels impayés antérieurs non régularisés
                regulariserImpaYesAnterieurs(client.getId(), annee, mois);
            } else {
                // Absent → créer un impayé si pas déjà enregistré
                if (!impayeRepository.existsByClientIdAndAnneeAndMois(client.getId(), annee, mois)) {
                    Impaye impaye = Impaye.builder()
                        .client(client)
                        .annee(annee)
                        .mois(mois)
                        .statut(StatutImpaye.IMPAYE)
                        .dateDetection(LocalDateTime.now())
                        .agenceLib(client.getAgenceLib())
                        .gestionnaire(client.getGestionnaire())
                        .zoneLib(client.getZoneLib())
                        .build();
                    impayeRepository.save(impaye);
                    nbCreés++;
                }
            }
        }

        log.info("=== FIN DÉTECTION : {} impayés créés pour {}/{} ===", nbCreés, mois, annee);
        return nbCreés;
    }

    /**
     * Recalcule tous les impayés depuis le début.
     * Utilisé par l'admin pour une remise à zéro complète.
     */
    @Transactional
    public void recalculerTout() {
        log.warn("RECALCUL COMPLET - suppression de tous les impayés");
        impayeRepository.deleteAll();

        // Récupérer tous les mois importés dans l'ordre chronologique
        List<Object[]> moisImportes = stockMensuelRepository.findAllDistinctAnneesMois();
        log.info("Recalcul sur {} mois importés", moisImportes.size());

        for (Object[] row : moisImportes) {
            Integer annee = (Integer) row[0];
            Integer mois  = (Integer) row[1];
            detecterImpaYesDuMois(annee, mois);
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Méthodes privées
    // ────────────────────────────────────────────────────────────────

    /**
     * Vérifie si le client avait souscrit avant ou pendant le mois cible.
     */
    private boolean avaitSouscritAvant(Long clientId, Integer annee, Integer mois) {
        List<Souscription> souscriptions =
            souscriptionRepository.findByClientIdOrderByDatSouscriptionAsc(clientId);

        LocalDate limiteHaute = LocalDate.of(annee, mois, 1);

        return souscriptions.stream().anyMatch(s -> {
            if (s.getDatSouscription() != null) {
                return !s.getDatSouscription().isAfter(limiteHaute);
            }
            return false;
        });
    }

    /**
     * Passe les impayés non-régularisés antérieurs au statut REGULARISE.
     * L'historique est conservé mais marqué comme résolu.
     */
    private void regulariserImpaYesAnterieurs(Long clientId, Integer annee, Integer mois) {
        impayeRepository.findByClientIdOrderByAnneeDescMoisDesc(clientId).stream()
            .filter(i -> i.getStatut() == StatutImpaye.IMPAYE)
            .filter(i -> estStrictementAnterieur(i.getAnnee(), i.getMois(), annee, mois))
            .forEach(i -> {
                i.setStatut(StatutImpaye.REGULARISE);
                i.setDateRegularisation(LocalDateTime.now());
                i.setCommentaire("Régularisation automatique — client présent en " + mois + "/" + annee);
                impayeRepository.save(i);
            });
    }

    private boolean estStrictementAnterieur(int a1, int m1, int a2, int m2) {
        return a1 < a2 || (a1 == a2 && m1 < m2);
    }
}
