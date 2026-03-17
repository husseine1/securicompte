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
        List<HistoriquePaiementDto> historique = construireHistorique(clientId, souscriptions);

        BigDecimal montantTotal = impayes.stream()
            .filter(i -> i.getStatut() == StatutImpaye.IMPAYE && i.getMontantDu() != null)
            .map(Impaye::getMontantDu)
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
            .historiquePaiements(historique)
            .impayes(impayes.stream().map(this::toImpayeDto).collect(Collectors.toList()))
            .nbImpayes((int) impayes.stream().filter(i -> i.getStatut() == StatutImpaye.IMPAYE).count())
            .montantTotalDu(montantTotal)
            .build();
    }

    /**
     * Construit l'historique des paiements mois par mois depuis la souscription
     */
    private List<HistoriquePaiementDto> construireHistorique(Long clientId, List<Souscription> souscriptions) {
        if (souscriptions.isEmpty()) return new ArrayList<>();

        // Trouver la date de première souscription
        LocalDate premiereSouscription = souscriptions.stream()
            .map(Souscription::getDatSouscription)
            .filter(Objects::nonNull)
            .min(LocalDate::compareTo)
            .orElse(null);

        if (premiereSouscription == null) return new ArrayList<>();

        LocalDate maintenant = LocalDate.now();
        List<HistoriquePaiementDto> historique = new ArrayList<>();

        // Parcourir mois par mois depuis la souscription jusqu'à aujourd'hui
        LocalDate curseur = premiereSouscription.withDayOfMonth(1);
        while (!curseur.isAfter(maintenant)) {
            int annee = curseur.getYear();
            int mois = curseur.getMonthValue();

            boolean presentDansStock = stockMensuelRepository
                .existsByClientIdAndAnneeAndMois(clientId, annee, mois);

            // Vérifier si un import existe pour ce mois
            boolean importExiste = importFichierRepository.existsByAnneeAndMois(annee, mois);

            String statut;
            if (!importExiste) {
                statut = "NON_IMPORTE";
            } else if (presentDansStock) {
                statut = "PAYE";
            } else {
                statut = "IMPAYE";
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
