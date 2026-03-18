package com.securicompte.service;

import com.securicompte.dto.*;
import com.securicompte.entity.*;
import com.securicompte.enums.StatutImpaye;
import com.securicompte.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ClientService {

    private static final String[] MOIS_NOMS = {
        "", "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
        "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"
    };

    private final ClientRepository clientRepository;
    private final SouscriptionRepository souscriptionRepository;
    private final StockMensuelRepository stockMensuelRepository;
    private final ImpayeRepository impayeRepository;
    private final ImportFichierRepository importFichierRepository;

    /**
     * Recherche un client par numéro ou nom
     */
    public Page<ClientDto> rechercherClients(String recherche, int page, int size) {
        Page<Client> clients = clientRepository.rechercherClients(
            recherche, PageRequest.of(page, size, Sort.by("nom")));
        return clients.map(c -> ClientDto.builder()
            .id(c.getId())
            .numeroClient(c.getNumeroClient())
            .nom(c.getNom())
            .zoneLib(c.getZoneLib())
            .agenceLib(c.getAgenceLib())
            .gestionnaire(c.getGestionnaire())
            .build());
    }

    /**
     * Récupère le détail complet d'un client avec historique
     */
    public ClientDetailDto getClientDetail(Long clientId) {
        Client client = clientRepository.findById(clientId)
            .orElseThrow(() -> new RuntimeException("Client non trouvé: " + clientId));

        List<Souscription> souscriptions = souscriptionRepository.findByClientId(clientId);
        List<Impaye> impayes = impayeRepository.findByClientIdOrderByAnneeDescMoisDesc(clientId);
        List<HistoriquePaiementDto> historique = construireHistorique(clientId, souscriptions, impayes);
        List<StockMensuel> stock = stockMensuelRepository.findByClientIdOrderByAnneeDescMoisDesc(clientId);

        // Compléter la liste des impayés avec les mois détectés dans l'historique
        // mais absents de la table impaye (détection manquée)
        List<ImpayeDto> impayesDtos = completerImpaYesDepuisHistorique(client, impayes, historique);

        // Enrichir les impayés avec securicompte et montantDu depuis la souscription du client
        // (les impayés créés par détection automatique n'ont pas la souscription liée)
        Souscription souscriptionRef = souscriptions.stream()
            .filter(s -> s.getDatSouscription() != null)
            .max(Comparator.comparing(Souscription::getDatSouscription))
            .orElse(null);
        if (souscriptionRef != null) {
            String numSecuricompte = souscriptionRef.getSecuricompte();
            BigDecimal montantRef = souscriptionRef.getCommissions();
            for (ImpayeDto dto : impayesDtos) {
                if (dto.getSecuricompte() == null && numSecuricompte != null) {
                    dto.setSecuricompte(numSecuricompte);
                }
                if (dto.getMontantDu() == null && montantRef != null) {
                    dto.setMontantDu(montantRef);
                }
            }
        }

        long nbImpayes = impayesDtos.stream()
            .filter(i -> i.getStatut() == StatutImpaye.IMPAYE)
            .count();

        BigDecimal montantTotal = impayesDtos.stream()
            .filter(i -> i.getStatut() == StatutImpaye.IMPAYE && i.getSecuricompte() != null)
            .map(i -> {
                try { return new BigDecimal(i.getSecuricompte().trim()); }
                catch (NumberFormatException e) { return BigDecimal.ZERO; }
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ClientDetailDto.builder()
            .id(client.getId())
            .numeroClient(client.getNumeroClient())
            .nom(client.getNom())
            .dateNaissance(client.getDateNaissance())
            .compte(client.getCompte())
            .zoneLib(client.getZoneLib())
            .agenceLib(client.getAgenceLib())
            .gestionnaire(client.getGestionnaire())
            .souscriptions(souscriptions.stream().map(this::toSouscriptionDto).collect(Collectors.toList()))
            .stockMensuel(stock.stream().map(this::toStockDto).collect(Collectors.toList()))
            .historiquePaiements(historique)
            .impayes(impayesDtos)
            .nbImpayes((int) nbImpayes)
            .montantTotalDu(montantTotal)
            .build();
    }

    /**
     * Construit l'historique des paiements mois par mois depuis la souscription
     */
    private List<HistoriquePaiementDto> construireHistorique(Long clientId,
                                                               List<Souscription> souscriptions,
                                                               List<Impaye> impayes) {
        if (souscriptions.isEmpty()) return new ArrayList<>();

        // Trouver la date de première souscription
        LocalDate premiereSouscription = souscriptions.stream()
            .map(Souscription::getDatSouscription)
            .filter(Objects::nonNull)
            .min(LocalDate::compareTo)
            .orElse(null);

        if (premiereSouscription == null) return new ArrayList<>();

        // Index des statuts impayés par "annee_mois" pour éviter N+1 requêtes
        Map<String, StatutImpaye> statutsImpaye = impayes.stream()
            .collect(Collectors.toMap(
                i -> i.getAnnee() + "_" + i.getMois(),
                Impaye::getStatut,
                (a, b) -> a
            ));

        LocalDate maintenant = LocalDate.now();
        List<HistoriquePaiementDto> historique = new ArrayList<>();

        // Parcourir mois par mois depuis la souscription jusqu'à aujourd'hui
        LocalDate curseur = premiereSouscription.withDayOfMonth(1);
        while (!curseur.isAfter(maintenant)) {
            int annee = curseur.getYear();
            int mois = curseur.getMonthValue();

            boolean presentDansStock = stockMensuelRepository
                .existsByClientIdAndAnneeAndMois(clientId, annee, mois);
            boolean importExiste = importFichierRepository.existsByAnneeAndMois(annee, mois);

            String statut;
            if (!importExiste) {
                statut = "NON_IMPORTE";
            } else if (presentDansStock) {
                statut = "PAYE";
            } else {
                // Utiliser le statut de la table impayé s'il existe
                StatutImpaye statutDb = statutsImpaye.get(annee + "_" + mois);
                if (statutDb == StatutImpaye.REGULARISE) {
                    statut = "REGULARISE";
                } else {
                    statut = "IMPAYE";
                }
            }

            historique.add(HistoriquePaiementDto.builder()
                .annee(annee)
                .mois(mois)
                .moisNom(getMoisNom(mois))
                .statut(statut)
                .present(presentDansStock)
                .build());

            curseur = curseur.plusMonths(1);
        }

        // Ordre décroissant
        Collections.reverse(historique);
        return historique;
    }

    /**
     * Complète la liste des impayés DB avec les mois détectés dans l'historique
     * (absent du stock) mais qui n'ont pas d'enregistrement dans la table impaye.
     */
    private List<ImpayeDto> completerImpaYesDepuisHistorique(Client client,
                                                               List<Impaye> impayesDb,
                                                               List<HistoriquePaiementDto> historique) {
        // Clés déjà présentes en base
        Set<String> clesExistantes = impayesDb.stream()
            .map(i -> i.getAnnee() + "_" + i.getMois())
            .collect(Collectors.toSet());

        List<ImpayeDto> resultat = impayesDb.stream()
            .map(this::toImpayeDto)
            .collect(Collectors.toList());

        // Ajouter les mois IMPAYÉ de l'historique absents de la table
        for (HistoriquePaiementDto h : historique) {
            if ("IMPAYE".equals(h.getStatut()) && !clesExistantes.contains(h.getAnnee() + "_" + h.getMois())) {
                resultat.add(ImpayeDto.builder()
                    .clientId(client.getId())
                    .numeroClient(client.getNumeroClient())
                    .nomClient(client.getNom())
                    .agenceLib(client.getAgenceLib())
                    .gestionnaire(client.getGestionnaire())
                    .zoneLib(client.getZoneLib())
                    .annee(h.getAnnee())
                    .mois(h.getMois())
                    .moisNom(h.getMoisNom())
                    .statut(StatutImpaye.IMPAYE)
                    .build());
            }
        }

        // Trier par annee desc, mois desc
        resultat.sort(Comparator.comparingInt(ImpayeDto::getAnnee).reversed()
            .thenComparingInt(ImpayeDto::getMois).reversed());

        return resultat;
    }

    public List<String> getAgences() {
        return clientRepository.findDistinctAgences();
    }

    public List<String> getGestionnaires() {
        return clientRepository.findDistinctGestionnaires();
    }

    private ClientDto toClientDto(Client client) {
        List<Impaye> impayes = impayeRepository.findByClientIdOrderByAnneeDescMoisDesc(client.getId());
        long nbImpayes = impayes.stream().filter(i -> i.getStatut() == StatutImpaye.IMPAYE).count();
        BigDecimal montant = impayes.stream()
            .filter(i -> i.getStatut() == StatutImpaye.IMPAYE && i.getMontantDu() != null)
            .map(Impaye::getMontantDu)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ClientDto.builder()
            .id(client.getId())
            .numeroClient(client.getNumeroClient())
            .nom(client.getNom())
            .dateNaissance(client.getDateNaissance())
            .compte(client.getCompte())
            .zoneLib(client.getZoneLib())
            .agenceLib(client.getAgenceLib())
            .gestionnaire(client.getGestionnaire())
            .nbImpayes((int) nbImpayes)
            .montantTotalDu(montant)
            .build();
    }

    private SouscriptionDto toStockDto(StockMensuel s) {
        return SouscriptionDto.builder()
            .annee(s.getAnnee())
            .mois(s.getMois())
            .moisNom(getMoisNom(s.getMois()))
            .securicompte(s.getSecuricompte())
            .commissions(s.getCommissions())
            .libelPackage(s.getLibelPackage())
            .optionSecuricompte(s.getOptionSecuricompte())
            .datSouscription(s.getDatSouscription())
            .build();
    }

    private SouscriptionDto toSouscriptionDto(Souscription s) {
        return SouscriptionDto.builder()
            .id(s.getId())
            .securicompte(s.getSecuricompte())
            .commissions(s.getCommissions())
            .libelPackage(s.getLibelPackage())
            .optionSecuricompte(s.getOptionSecuricompte())
            .datSouscription(s.getDatSouscription())
            .datOuverture(s.getDatOuverture())
            .typeSouscription(s.getTypeSouscription() != null ? s.getTypeSouscription().getLibelle() : "")
            .build();
    }

    public ImpayeDto toImpayeDto(Impaye i) {
        return ImpayeDto.builder()
            .id(i.getId())
            .clientId(i.getClient() != null ? i.getClient().getId() : null)
            .numeroClient(i.getClient() != null ? i.getClient().getNumeroClient() : null)
            .nomClient(i.getClient() != null ? i.getClient().getNom() : null)
            .agenceLib(i.getAgenceLib())
            .gestionnaire(i.getGestionnaire())
            .zoneLib(i.getZoneLib())
            .annee(i.getAnnee())
            .mois(i.getMois())
            .moisNom(getMoisNom(i.getMois()))
            .statut(i.getStatut())
            .montantDu(i.getMontantDu())
            .securicompte(i.getSouscription() != null ? i.getSouscription().getSecuricompte() : null)
            .dateDetection(i.getDateDetection())
            .dateRegularisation(i.getDateRegularisation())
            .regularisePar(i.getRegularisePar() != null ? i.getRegularisePar().getUsername() : null)
            .commentaire(i.getCommentaire())
            .build();
    }

    private String getMoisNom(Integer mois) {
        if (mois != null && mois >= 1 && mois <= 12) return MOIS_NOMS[mois];
        return "";
    }
}
