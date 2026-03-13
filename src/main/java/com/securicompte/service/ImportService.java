package com.securicompte.service;

import com.securicompte.dto.ImportResultDto;
import com.securicompte.entity.*;
import com.securicompte.enums.StatutImport;
import com.securicompte.enums.TypeSouscription;
import com.securicompte.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportService {

    private final ExcelParserService excelParserService;
    private final ImpayeDetectionService impayeDetectionService;
    private final ImportFichierRepository importFichierRepository;
    private final ClientRepository clientRepository;
    private final SouscriptionRepository souscriptionRepository;
    private final StockMensuelRepository stockMensuelRepository;

    /**
     * Point d'entrée principal de l'import.
     * Lit le fichier Excel, importe les 3 feuilles, puis lance la détection.
     */
    @Transactional
    public ImportResultDto importerFichier(MultipartFile file, Integer annee, Integer mois, User importePar) {
        log.info("Début import fichier: {} pour {}/{}", file.getOriginalFilename(), mois, annee);

        // Vérifier si ce mois a déjà été importé
        if (importFichierRepository.existsByAnneeAndMois(annee, mois)) {
            // Supprimer l'ancien import et re-importer
            ImportFichier ancien = importFichierRepository.findByAnneeAndMois(annee, mois).get();
            supprimerDonneesMois(annee, mois);
            importFichierRepository.delete(ancien);
            log.info("Ancien import {}/{} supprimé, réimport en cours...", mois, annee);
        }

        // Créer l'enregistrement d'import
        ImportFichier importFichier = ImportFichier.builder()
            .nomFichier(file.getOriginalFilename())
            .annee(annee)
            .mois(mois)
            .statut(StatutImport.EN_COURS)
            .importePar(importePar)
            .build();
        importFichier = importFichierRepository.save(importFichier);

        try {
            // Parser le fichier Excel
            ExcelParserService.ExcelData excelData = excelParserService.parseExcel(file);

            // Importer les nouvelles souscriptions
            int nbNouvelles = importerSouscriptions(
                excelData.nouvelles(), TypeSouscription.NOUVELLE, importFichier);

            // Importer les anciennes souscriptions
            int nbAnciennes = importerSouscriptions(
                excelData.anciennes(), TypeSouscription.ANCIENNE, importFichier);

            // Importer le stock mensuel
            int nbStock = importerStock(excelData.stock(), annee, mois, importFichier);

            // Lancer la détection des impayés
            int nbImpaYes = impayeDetectionService.detecterImpaYesDuMois(annee, mois);

            // Mettre à jour le statut de l'import
            importFichier.setStatut(StatutImport.SUCCES);
            importFichier.setNbNouvelles(nbNouvelles);
            importFichier.setNbAnciennes(nbAnciennes);
            importFichier.setNbStock(nbStock);
            importFichier.setDateFinImport(LocalDateTime.now());
            importFichierRepository.save(importFichier);

            log.info("Import terminé avec succès: Nouvelles={}, Anciennes={}, Stock={}, Impayés={}",
                nbNouvelles, nbAnciennes, nbStock, nbImpaYes);

            return ImportResultDto.builder()
                .importId(importFichier.getId())
                .nomFichier(file.getOriginalFilename())
                .annee(annee)
                .mois(mois)
                .statut("SUCCES")
                .nbNouvelles(nbNouvelles)
                .nbAnciennes(nbAnciennes)
                .nbStock(nbStock)
                .nbImpaYesDetectes(nbImpaYes)
                .nbErreurs(0)
                .dateImport(importFichier.getDateImport())
                .succes(true)
                .build();

        } catch (Exception e) {
            log.error("Erreur lors de l'import: {}", e.getMessage(), e);
            importFichier.setStatut(StatutImport.ECHEC);
            importFichier.setMessageErreur(e.getMessage());
            importFichier.setDateFinImport(LocalDateTime.now());
            importFichierRepository.save(importFichier);

            return ImportResultDto.builder()
                .importId(importFichier.getId())
                .nomFichier(file.getOriginalFilename())
                .annee(annee)
                .mois(mois)
                .statut("ECHEC")
                .messageErreur(e.getMessage())
                .succes(false)
                .build();
        }
    }

    /**
     * Importe les souscriptions (nouvelles ou anciennes)
     */
    private int importerSouscriptions(List<Map<String, Object>> rows,
                                       TypeSouscription type,
                                       ImportFichier importFichier) {
        int count = 0;
        for (Map<String, Object> row : rows) {
            try {
                String numeroClient = excelParserService.getString(row, "CLIENT");
                if (numeroClient == null) continue;

                // Trouver ou créer le client
                Client client = trouverOuCreerClient(row);

                // Créer la souscription si elle n'existe pas encore
                java.time.LocalDate datSouscription = null;
                Object datVal = row.get("DATSOUSCRIPTION");
                if (datVal instanceof java.time.LocalDate ld) datSouscription = ld;

                if (datSouscription != null && !souscriptionRepository
                    .existsByClientIdAndDatSouscriptionAndTypeSouscription(
                        client.getId(), datSouscription, type)) {

                    Souscription souscription = excelParserService
                        .rowToSouscription(row, client, type, importFichier);
                    souscriptionRepository.save(souscription);
                    count++;
                }
            } catch (Exception e) {
                log.warn("Erreur import souscription ligne: {}", e.getMessage());
            }
        }
        return count;
    }

    /**
     * Importe le stock mensuel
     */
    private int importerStock(List<Map<String, Object>> rows, int annee, int mois,
                               ImportFichier importFichier) {
        int count = 0;
        for (Map<String, Object> row : rows) {
            try {
                String numeroClient = excelParserService.getString(row, "CLIENT");
                if (numeroClient == null) continue;

                Client client = trouverOuCreerClient(row);

                // Créer ou mettre à jour le stock
                Optional<StockMensuel> existant = stockMensuelRepository
                    .findByClientIdAndAnneeAndMois(client.getId(), annee, mois);

                if (existant.isEmpty()) {
                    StockMensuel stock = excelParserService
                        .rowToStock(row, client, annee, mois, importFichier);
                    stockMensuelRepository.save(stock);
                    count++;
                }
            } catch (Exception e) {
                log.warn("Erreur import stock ligne: {}", e.getMessage());
            }
        }
        return count;
    }

    /**
     * Trouve un client existant ou en crée un nouveau
     */
    private Client trouverOuCreerClient(Map<String, Object> row) {
        String numeroClient = excelParserService.getString(row, "CLIENT");

        Optional<Client> existant = clientRepository.findByNumeroClient(numeroClient);
        if (existant.isPresent()) {
            // Mettre à jour les infos si nécessaires
            Client client = existant.get();
            updateClientInfos(client, row);
            return clientRepository.save(client);
        }

        // Créer un nouveau client
        Client newClient = excelParserService.rowToClient(row);
        return clientRepository.save(newClient);
    }

    private void updateClientInfos(Client client, Map<String, Object> row) {
        String nom = excelParserService.getString(row, "NOM");
        String agenceLib = excelParserService.getString(row, "AGENCELIB");
        String gestionnaire = excelParserService.getString(row, "GESTIONNAIRE");
        String zoneLib = excelParserService.getString(row, "ZONELIB");

        if (nom != null) client.setNom(nom);
        if (agenceLib != null) client.setAgenceLib(agenceLib);
        if (gestionnaire != null) client.setGestionnaire(gestionnaire);
        if (zoneLib != null) client.setZoneLib(zoneLib);
    }

    /**
     * Supprime toutes les données d'un mois avant réimport
     */
    private void supprimerDonneesMois(Integer annee, Integer mois) {
        // Supprimer les stocks du mois
        List<StockMensuel> stocks = stockMensuelRepository.findByAnneeAndMois(annee, mois);
        stockMensuelRepository.deleteAll(stocks);
        log.info("Suppression de {} enregistrements stock {}/{}", stocks.size(), mois, annee);
    }

    public List<ImportFichier> getTousLesImports() {
        return importFichierRepository.findAllByOrderByAnneeDescMoisDesc();
    }

    public List<ImportFichier> getImportsByAnnee(Integer annee) {
        return importFichierRepository.findByAnneeOrderByMoisDesc(annee);
    }
}
