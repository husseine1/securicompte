package com.securicompte.service;

import com.securicompte.dto.*;
import com.securicompte.entity.*;
import com.securicompte.enums.StatutImpaye;
import com.securicompte.mapper.ImpayeMapper;
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
    private final ImpayeMapper impayeMapper;

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
        List<StockMensuel> stock = stockMensuelRepository.findByClientIdOrderByAnneeDescMoisDesc(clientId);

        // Charger tous les mois importés en UNE seule requête (au lieu de 1 requête par mois dans la boucle)
        Set<String> moisImportes = importFichierRepository.findAllAnneesMois().stream()
            .map(arr -> arr[0] + "_" + arr[1])
            .collect(Collectors.toSet());

        List<HistoriquePaiementDto> historique = construireHistorique(souscriptions, impayes, stock, client.getDateSinistre(), moisImportes);

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
            .dateSinistre(client.getDateSinistre())
            .souscriptions(souscriptions.stream().map(this::toSouscriptionDto).collect(Collectors.toList()))
            .stockMensuel(stock.stream().map(this::toStockDto).collect(Collectors.toList()))
            .historiquePaiements(historique)
            .impayes(impayesDtos)
            .nbImpayes((int) nbImpayes)
            .montantTotalDu(montantTotal)
            .build();
    }

    /**
     * Construit l'historique des paiements mois par mois depuis la souscription.
     * Optimisé : 0 requête DB dans la boucle (utilise des Sets précalculés).
     *
     * @param moisImportes  Set de "annee_mois" chargé en amont (1 seule requête)
     */
    private List<HistoriquePaiementDto> construireHistorique(List<Souscription> souscriptions,
                                                               List<Impaye> impayes,
                                                               List<StockMensuel> stock,
                                                               LocalDate dateSinistre,
                                                               Set<String> moisImportes) {
        if (souscriptions.isEmpty()) return new ArrayList<>();

        LocalDate premiereSouscription = souscriptions.stream()
            .map(Souscription::getDatSouscription)
            .filter(Objects::nonNull)
            .min(LocalDate::compareTo)
            .orElse(null);

        if (premiereSouscription == null) return new ArrayList<>();

        // Index statuts impayés par "annee_mois"
        Map<String, StatutImpaye> statutsImpaye = impayes.stream()
            .collect(Collectors.toMap(
                i -> i.getAnnee() + "_" + i.getMois(),
                Impaye::getStatut,
                (a, b) -> a
            ));

        // Index stock en mémoire — O(1) au lieu d'1 requête DB par mois
        Set<String> moisEnStock = stock.stream()
            .map(s -> s.getAnnee() + "_" + s.getMois())
            .collect(Collectors.toSet());

        LocalDate maintenant = LocalDate.now();
        List<HistoriquePaiementDto> historique = new ArrayList<>();

        // Ne pas afficher les mois antérieurs au premier import du système
        LocalDate debutHisto = premiereSouscription.withDayOfMonth(1);
        Optional<LocalDate> premierImport = moisImportes.stream()
            .map(cle -> { String[] p = cle.split("_"); return LocalDate.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]), 1); })
            .min(LocalDate::compareTo);
        if (premierImport.isPresent() && premierImport.get().isAfter(debutHisto)) {
            debutHisto = premierImport.get();
        }

        LocalDate curseur = debutHisto;
        while (!curseur.isAfter(maintenant)) {
            int annee = curseur.getYear();
            int mois = curseur.getMonthValue();
            String cle = annee + "_" + mois;

            boolean presentDansStock = moisEnStock.contains(cle);
            boolean importExiste    = moisImportes.contains(cle);
            boolean moisSinistre    = dateSinistre != null
                && !curseur.isBefore(dateSinistre.withDayOfMonth(1));

            String statut;
            if (!importExiste) {
                statut = "NON_IMPORTE";
            } else if (moisSinistre) {
                statut = presentDansStock ? "TROP_PERCU" : "SINISTRE";
            } else if (presentDansStock) {
                statut = "PAYE";
            } else {
                StatutImpaye statutDb = statutsImpaye.get(cle);
                statut = (statutDb == StatutImpaye.REGULARISE) ? "REGULARISE" : "IMPAYE";
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

    @Transactional
    public void enregistrerSinistre(Long clientId, String dateSinistreStr) {
        Client client = clientRepository.findById(clientId)
            .orElseThrow(() -> new RuntimeException("Client non trouvé: " + clientId));
        LocalDate dateSinistre = (dateSinistreStr != null && !dateSinistreStr.isBlank())
            ? LocalDate.parse(dateSinistreStr) : null;
        client.setDateSinistre(dateSinistre);
        clientRepository.save(client);
    }

    public List<String> getAgences() {
        return clientRepository.findDistinctAgences();
    }

    public List<String> getGestionnaires() {
        return clientRepository.findDistinctGestionnaires();
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
        return impayeMapper.toDto(i);
    }

    private String getMoisNom(Integer mois) {
        if (mois != null && mois >= 1 && mois <= 12) return MOIS_NOMS[mois];
        return "";
    }
}
