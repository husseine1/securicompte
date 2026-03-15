package com.securicompte.service;

import com.securicompte.entity.Client;
import com.securicompte.entity.Impaye;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service de détection automatique des impayés.
 *
 * ALGORITHME :
 * Pour chaque client ayant souscrit avant ou pendant le mois M :
 *   - S'il EST dans le stock du mois M  → PAYÉ (régulariser ses impayés précédents)
 *   - S'il N'EST PAS dans le stock du mois M → IMPAYÉ enregistré
 *
 * Toutes les opérations sont faites en bulk (5-6 requêtes au total).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImpayeDetectionService {

    private static final int BATCH_SIZE = 5000;

    private final ClientRepository        clientRepository;
    private final SouscriptionRepository  souscriptionRepository;
    private final StockMensuelRepository  stockMensuelRepository;
    private final ImpayeRepository        impayeRepository;

    /**
     * Détecte les impayés pour le mois/année donné.
     * Appelé automatiquement après chaque import.
     * Utilise des requêtes bulk — O(1) requêtes quel que soit le nombre de clients.
     *
     * @return nombre d'impayés nouvellement créés
     */
    @Transactional
    public int detecterImpaYesDuMois(Integer annee, Integer mois) {
        log.info("=== DÉTECTION IMPAYÉS {}/{} ===", mois, annee);

        LocalDate limiteHaute = LocalDate.of(annee, mois, 1);

        // 1. Clients ayant souscrit avant ou pendant ce mois (une seule requête)
        Set<Long> clientsAvecSouscription = new HashSet<>(
            souscriptionRepository.findClientIdsWithSouscriptionBefore(limiteHaute));
        log.info("Clients avec souscription <= {}/{}: {}", mois, annee, clientsAvecSouscription.size());

        if (clientsAvecSouscription.isEmpty()) {
            log.info("Aucune souscription trouvée — aucun impayé à détecter.");
            return 0;
        }

        // 2. Clients présents dans le stock ce mois (une seule requête)
        Set<Long> clientsPayesIds = new HashSet<>(
            stockMensuelRepository.findClientIdsPresentsDansMois(annee, mois));
        log.info("Clients présents dans le stock: {}", clientsPayesIds.size());

        // 3. Régulariser en bulk les clients qui ont payé ce mois
        List<Long> aRegulariser = clientsAvecSouscription.stream()
            .filter(clientsPayesIds::contains)
            .collect(Collectors.toList());

        if (!aRegulariser.isEmpty()) {
            String comment = "Regularisation automatique - " + mois + "/" + annee;
            LocalDateTime now = LocalDateTime.now();
            int totalReg = 0;
            for (int i = 0; i < aRegulariser.size(); i += BATCH_SIZE) {
                List<Long> batch = aRegulariser.subList(i, Math.min(i + BATCH_SIZE, aRegulariser.size()));
                totalReg += impayeRepository.regulariserBulk(
                    batch, annee, mois, now, comment,
                    StatutImpaye.REGULARISE, StatutImpaye.IMPAYE);
            }
            log.info("{} impayés régularisés", totalReg);
        }

        // 4. Clients impayés = ont souscrit MAIS absents du stock
        Set<Long> impayesExistants = new HashSet<>(
            impayeRepository.findClientIdsWithImpayeForMois(annee, mois));

        List<Long> aCreer = clientsAvecSouscription.stream()
            .filter(id -> !clientsPayesIds.contains(id))
            .filter(id -> !impayesExistants.contains(id))
            .collect(Collectors.toList());

        log.info("Nouveaux impayés à créer: {}", aCreer.size());

        // 5. Créer les impayés en bulk (batches de BATCH_SIZE)
        int nbCrees = 0;
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < aCreer.size(); i += BATCH_SIZE) {
            List<Long> batch = aCreer.subList(i, Math.min(i + BATCH_SIZE, aCreer.size()));
            List<Client> clients = clientRepository.findAllById(batch);
            List<Impaye> impayes = clients.stream().map(client ->
                Impaye.builder()
                    .client(client)
                    .annee(annee)
                    .mois(mois)
                    .statut(StatutImpaye.IMPAYE)
                    .dateDetection(now)
                    .agenceLib(client.getAgenceLib())
                    .gestionnaire(client.getGestionnaire())
                    .zoneLib(client.getZoneLib())
                    .build()
            ).collect(Collectors.toList());
            impayeRepository.saveAll(impayes);
            nbCrees += impayes.size();
        }

        log.info("=== FIN DÉTECTION : {} impayés créés pour {}/{} ===", nbCrees, mois, annee);
        return nbCrees;
    }

    /**
     * Recalcule tous les impayés depuis le début.
     * Utilisé par l'admin pour une remise à zéro complète.
     */
    @Transactional
    public void recalculerTout() {
        log.warn("RECALCUL COMPLET - suppression de tous les impayés");
        impayeRepository.deleteAll();

        List<Object[]> moisImportes = stockMensuelRepository.findAllDistinctAnneesMois();
        log.info("Recalcul sur {} mois importés", moisImportes.size());

        for (Object[] row : moisImportes) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) continue;
            Integer annee = ((Number) row[0]).intValue();
            Integer mois  = ((Number) row[1]).intValue();
            detecterImpaYesDuMois(annee, mois);
        }
    }
}
