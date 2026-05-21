package com.securicompte.service;

import com.securicompte.dto.ChangementClientDto;
import com.securicompte.dto.ChangementPrimeDto;
import com.securicompte.dto.ImportResultDto;
import com.securicompte.entity.*;
import com.securicompte.enums.StatutChangement;
import com.securicompte.enums.StatutImport;
import com.securicompte.enums.TypeSouscription;
import com.securicompte.repository.*;
import com.securicompte.util.ByteArrayMultipartFile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportService {

    private final ExcelParserService            excelParserService;
    private final ImpayeDetectionService        impayeDetectionService;
    private final NotificationService           notificationService;
    private final ImportFichierRepository       importFichierRepository;
    private final ImportFichierBytesRepository  importFichierBytesRepository;
    private final ClientRepository              clientRepository;
    private final SouscriptionRepository        souscriptionRepository;
    private final StockMensuelRepository        stockMensuelRepository;
    private final ImpayeRepository              impayeRepository;
    private final ChangementPrimeRepository     changementPrimeRepository;
    private final ChangementClientRepository    changementClientRepository;
    private final PlatformTransactionManager    transactionManager;

    /**
     * Version asynchrone : lance l'import dans un thread dédié et retourne immédiatement.
     * Le statut EN_COURS est visible dans l'historique ; la page se rafraîchit automatiquement.
     * Les bytes du fichier sont copiés avant l'appel pour survivre à la fin de la requête HTTP.
     */
    @Async
    public void importerFichierAsync(byte[] fileBytes, String filename, String contentType,
                                     Integer annee, Integer mois, User importePar) {
        MultipartFile file = new ByteArrayMultipartFile(fileBytes, filename, contentType);
        try {
            ImportResultDto result = importerFichier(file, annee, mois, importePar);
            if (result.isSucces()) {
                sauvegarderFichier(result.getImportId(), fileBytes, filename);
            }
        } catch (Exception e) {
            log.error("Erreur import asynchrone: {}", e.getMessage(), e);
        }
    }

    private void sauvegarderFichier(Long importId, byte[] fileBytes, String filename) {
        try {
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
                ImportFichier imp = importFichierRepository.findById(importId).orElseThrow();
                importFichierBytesRepository.deleteById(importId);
                importFichierBytesRepository.save(ImportFichierBytes.builder()
                    .importFichier(imp)
                    .fichierBytes(fileBytes)
                    .tailleOctets((long) fileBytes.length)
                    .build());
                log.info("Fichier '{}' stocké en base ({} octets)", filename, fileBytes.length);
                return null;
            });
        } catch (Exception e) {
            log.warn("Impossible de sauvegarder le fichier en base: {}", e.getMessage());
        }
    }

    /**
     * Point d'entrée principal de l'import.
     * Chaque étape s'exécute dans sa propre transaction :
     *  Tx1 - initialisation de l'enregistrement d'import (toujours commité)
     *  Tx2 - suppression + import des données (rollback si erreur)
     *  Tx3 - mise à jour statut SUCCES ou ECHEC (toujours commité)
     */
    public ImportResultDto importerFichier(MultipartFile file, Integer annee, Integer mois, User importePar) {
        log.info("Début import fichier: {} pour {}/{}", file.getOriginalFilename(), mois, annee);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        boolean isReimport = importFichierRepository.existsByAnneeAndMois(annee, mois);

        // Tx1 : créer ou réinitialiser l'enregistrement d'import
        Long importId = tx.execute(status -> {
            ImportFichier f;
            if (isReimport) {
                f = importFichierRepository.findByAnneeAndMois(annee, mois).get();
                f.setNomFichier(file.getOriginalFilename());
                f.setStatut(StatutImport.EN_COURS);
                f.setImportePar(importePar);
                f.setMessageErreur(null);
                f.setNbNouvelles(0);
                f.setNbAnciennes(0);
                f.setNbStock(0);
                f.setNbErreurs(0);
                f.setDateFinImport(null);
                log.info("Réimport {}/{} en cours...", mois, annee);
            } else {
                f = ImportFichier.builder()
                    .nomFichier(file.getOriginalFilename())
                    .annee(annee)
                    .mois(mois)
                    .statut(StatutImport.EN_COURS)
                    .importePar(importePar)
                    .build();
            }
            return importFichierRepository.save(f).getId();
        });

        try {
            // Parser le fichier (hors transaction — lecture seule)
            ExcelParserService.ExcelData excelData = excelParserService.parseExcel(file);

            // Collecte des détails d'erreurs pour affichage dans l'historique
            final List<String> errorDetails = new ArrayList<>();

            // Tx2 : supprimer l'ancien stock + importer toutes les données (atomique)
            int[] counts = tx.execute(status -> {
                ImportFichier importFichier = importFichierRepository.findById(importId).orElseThrow();
                if (isReimport) {
                    supprimerDonneesMois(annee, mois, importFichier.getId());
                }
                Map<String, Client> cache = construireClientCache(excelData.stock(), annee, mois);
                int[] res = importerStockEtSouscriptionsBulk(
                    excelData.stock(), annee, mois, importFichier, cache,
                    excelData.numerosNouvelles(), errorDetails);
                int ni = impayeDetectionService.detecterImpaYesDuMois(annee, mois);
                return new int[]{res[0], res[1], res[2], ni, res[3]};
            });

            // Tx3 : marquer SUCCES
            final int[] c = counts;
            tx.execute(status -> {
                ImportFichier f = importFichierRepository.findById(importId).orElseThrow();
                f.setStatut(StatutImport.SUCCES);
                f.setNbNouvelles(c[0]);
                f.setNbAnciennes(c[1]);
                f.setNbStock(c[2]);
                f.setNbErreurs(c[4]);
                f.setDateFinImport(LocalDateTime.now());
                if (!errorDetails.isEmpty()) {
                    f.setMessageErreur(String.join("\n", errorDetails));
                }
                return importFichierRepository.save(f);
            });

            log.info("Import terminé avec succès: Nouvelles={}, Anciennes={}, Stock={}, Impayés={}, Erreurs={}",
                c[0], c[1], c[2], c[3], c[4]);

            // Tx4 : détecter les changements de prime (non bloquant — ne remet pas en cause l'import)
            try {
                final String username = importePar.getUsername();
                tx.execute(status -> {
                    detecterChangementsPrimeImport(annee, mois, username);
                    return null;
                });
            } catch (Exception e) {
                log.warn("Détection changements de prime non complétée (non bloquant) : {}", e.getMessage());
            }

            // Tx5 : notifier les changements de données client (non bloquant)
            try {
                final String username = importePar.getUsername();
                tx.execute(status -> {
                    long nb = changementClientRepository.countByAnneeAndMois(annee, mois);
                    if (nb > 0) {
                        notificationService.creerNotificationChangementClientImport(annee, mois, nb, username);
                    }
                    return null;
                });
            } catch (Exception e) {
                log.warn("Notification changements client non complétée (non bloquant) : {}", e.getMessage());
            }

            ImportFichier saved = importFichierRepository.findById(importId).orElseThrow();
            return ImportResultDto.builder()
                .importId(importId)
                .nomFichier(file.getOriginalFilename())
                .annee(annee)
                .mois(mois)
                .statut("SUCCES")
                .nbNouvelles(c[0])
                .nbAnciennes(c[1])
                .nbStock(c[2])
                .nbImpaYesDetectes(c[3])
                .nbErreurs(c[4])
                .dateImport(saved.getDateImport())
                .succes(true)
                .build();

        } catch (Exception e) {
            log.error("Erreur lors de l'import: {}", e.getMessage(), e);

            // Tx3 (échec) : marquer ECHEC dans une transaction indépendante
            tx.execute(status -> {
                ImportFichier f = importFichierRepository.findById(importId).orElseThrow();
                f.setStatut(StatutImport.ECHEC);
                f.setMessageErreur(e.getMessage());
                f.setDateFinImport(LocalDateTime.now());
                return importFichierRepository.save(f);
            });

            return ImportResultDto.builder()
                .importId(importId)
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
     * Charge tous les clients existants en une seule requête,
     * crée les nouveaux en bulk, retourne un cache Map<numeroClient, Client>.
     */
    private Map<String, Client> construireClientCache(List<Map<String, Object>> stock, int annee, int mois) {

        Map<String, Map<String, Object>> premiereLignePar = new LinkedHashMap<>();
        for (Map<String, Object> row : stock) {
            String num = excelParserService.getNumeroClient(row);
            if (num != null) premiereLignePar.putIfAbsent(num, row);
        }

        // Charger les clients existants par lots (limite PostgreSQL : 65 535 paramètres)
        List<String> numeros = new ArrayList<>(premiereLignePar.keySet());
        Map<String, Client> cache = new HashMap<>();
        int batchSize = 5000;
        for (int i = 0; i < numeros.size(); i += batchSize) {
            List<String> batch = numeros.subList(i, Math.min(i + batchSize, numeros.size()));
            clientRepository.findByNumeroClientIn(batch)
                .forEach(c -> cache.put(c.getNumeroClient(), c));
        }

        // Mettre à jour les infos des clients existants uniquement si les données ont changé
        List<Client> aMettrAJour = new ArrayList<>();
        List<ChangementClient> changementsACreer = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Client client : cache.values()) {
            Map<String, Object> row = premiereLignePar.get(client.getNumeroClient());
            if (row != null && clientAChange(client, row)) {
                // Enregistrer les changements AVANT mise à jour (pour pouvoir revenir en arrière)
                changementsACreer.addAll(collecterChangementsClient(client, row, annee, mois, now));
                updateClientInfos(client, row);
                aMettrAJour.add(client);
            }
        }
        if (!aMettrAJour.isEmpty()) {
            clientRepository.saveAll(aMettrAJour);
            log.info("{} client(s) mis à jour (données modifiées)", aMettrAJour.size());
        }
        if (!changementsACreer.isEmpty()) {
            changementClientRepository.saveAll(changementsACreer);
            log.info("{} changement(s) de données client persistés (EN_ATTENTE)", changementsACreer.size());
        }

        // Créer les nouveaux clients en bulk
        List<Client> nouveaux = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : premiereLignePar.entrySet()) {
            if (!cache.containsKey(entry.getKey())) {
                nouveaux.add(excelParserService.rowToClient(entry.getValue()));
            }
        }
        if (!nouveaux.isEmpty()) {
            // Double-check en base pour éviter les violations de contrainte unique
            // (les clients peuvent avoir été créés par un import concurrent ou un import précédent)
            List<String> numerosNouveaux = nouveaux.stream()
                .map(Client::getNumeroClient).filter(Objects::nonNull).collect(Collectors.toList());
            for (int i = 0; i < numerosNouveaux.size(); i += 5000) {
                List<String> batch = numerosNouveaux.subList(i, Math.min(i + 5000, numerosNouveaux.size()));
                clientRepository.findByNumeroClientIn(batch).forEach(c -> cache.put(c.getNumeroClient(), c));
            }
            List<Client> vraiNouveaux = nouveaux.stream()
                .filter(c -> c.getNumeroClient() != null && !cache.containsKey(c.getNumeroClient()))
                .collect(Collectors.toList());
            if (!vraiNouveaux.isEmpty()) {
                List<Client> saved = clientRepository.saveAll(vraiNouveaux);
                saved.forEach(c -> cache.put(c.getNumeroClient(), c));
                log.info("{} nouveau(x) client(s) créé(s)", saved.size());
            }
        }

        log.info("Cache clients: {} clients chargés/créés", cache.size());
        return cache;
    }

    /**
     * Importe le stock mensuel ET crée les souscriptions depuis la même feuille.
     * NOUVELLE si dat_souscription.annee == annee ET dat_souscription.mois == mois, sinon ANCIENNE.
     * Retourne int[]{nbNouvelles, nbAnciennes, nbStock, nbErreurs}.
     */
    private int[] importerStockEtSouscriptionsBulk(List<Map<String, Object>> rows, int annee, int mois,
                                                     ImportFichier importFichier,
                                                     Map<String, Client> clientCache,
                                                     Set<String> numerosNouvelles,
                                                     List<String> errorDetails) {
        List<Long> candidateIds = rows.stream()
            .map(r -> excelParserService.getNumeroClient(r))
            .filter(Objects::nonNull)
            .map(clientCache::get)
            .filter(Objects::nonNull)
            .map(Client::getId)
            .distinct()
            .collect(Collectors.toList());

        Set<String> existingKeys = new HashSet<>();
        for (int i = 0; i < candidateIds.size(); i += 5000) {
            List<Long> batch = candidateIds.subList(i, Math.min(i + 5000, candidateIds.size()));
            existingKeys.addAll(souscriptionRepository.findExistingKeysForClients(batch));
        }

        Set<Long> seenStock = new HashSet<>();
        List<StockMensuel> stocksToSave = new ArrayList<>();
        List<Souscription> souscToSave  = new ArrayList<>();
        int nbNouvelles = 0, nbAnciennes = 0, erreurs = 0;

        for (Map<String, Object> row : rows) {
            try {
                String num = excelParserService.getNumeroClient(row);
                if (num == null) {
                    String rawVal = excelParserService.getString(row, "COMPTE");
                    if (rawVal == null) rawVal = excelParserService.getString(row, "CLIENT");
                    if (rawVal == null) {
                        // Ligne sans compte ni client (total/sous-total probable) — ignorée silencieusement
                        continue;
                    }
                    errorDetails.add("Stock – numéro illisible, valeur brute : " + rawVal);
                    log.warn("Stock – impossible d'extraire le numéro client : {}", rawVal);
                    erreurs++;
                    continue;
                }
                Client client = clientCache.get(num);
                if (client == null) {
                    errorDetails.add("Stock – client introuvable pour le numéro extrait : " + num);
                    log.warn("Client introuvable pour le numéro: {}", num);
                    erreurs++;
                    continue;
                }

                if (!seenStock.contains(client.getId())) {
                    // Stock mensuel
                    stocksToSave.add(excelParserService.rowToStock(row, client, annee, mois, importFichier));
                    seenStock.add(client.getId());

                    // Souscription : type déduit depuis la feuille "nouvelles souscriptions" du fichier
                    TypeSouscription type = numerosNouvelles.contains(num)
                        ? TypeSouscription.NOUVELLE : TypeSouscription.ANCIENNE;

                    Souscription s = excelParserService.rowToSouscription(row, client, type, importFichier);
                    String key = client.getId() + "_" + s.getDatSouscription() + "_" + type;
                    if (!existingKeys.contains(key)) {
                        souscToSave.add(s);
                        existingKeys.add(key);
                        if (type == TypeSouscription.NOUVELLE) nbNouvelles++;
                        else nbAnciennes++;
                    }
                }
            } catch (Exception e) {
                erreurs++;
                String rawNum = excelParserService.getNumeroClient(row);
                errorDetails.add("Stock – client=" + rawNum + " : " + e.getMessage());
                log.warn("Erreur import stock ligne: {}", e.getMessage());
            }
        }

        stockMensuelRepository.saveAll(stocksToSave);
        souscriptionRepository.saveAll(souscToSave);
        log.info("Stock importé: {} entrées, {} nouvelles souscriptions, {} anciennes, {} erreurs",
            stocksToSave.size(), nbNouvelles, nbAnciennes, erreurs);
        return new int[]{nbNouvelles, nbAnciennes, stocksToSave.size(), erreurs};
    }

    private boolean clientAChange(Client client, Map<String, Object> row) {
        String nom           = excelParserService.getString(row, "NOM");
        String agenceLib     = excelParserService.getString(row, "AGENCELIB");
        String gestionnaire  = excelParserService.getString(row, "GESTIONNAIRE");
        String zoneLib       = excelParserService.getString(row, "ZONELIB");
        java.time.LocalDate dateNaissance = excelParserService.getDate(row, "DATNAISSANCE");
        return (nom != null && !nom.equals(client.getNom()))
            || (agenceLib != null && !agenceLib.equals(client.getAgenceLib()))
            || (gestionnaire != null && !gestionnaire.equals(client.getGestionnaire()))
            || (zoneLib != null && !zoneLib.equals(client.getZoneLib()))
            || (dateNaissance != null && !dateNaissance.equals(client.getDateNaissance()));
    }

    private void updateClientInfos(Client client, Map<String, Object> row) {
        String nom           = excelParserService.getString(row, "NOM");
        String agenceLib     = excelParserService.getString(row, "AGENCELIB");
        String gestionnaire  = excelParserService.getString(row, "GESTIONNAIRE");
        String zoneLib       = excelParserService.getString(row, "ZONELIB");
        java.time.LocalDate dateNaissance = excelParserService.getDate(row, "DATNAISSANCE");

        if (nom != null)           client.setNom(nom);
        if (agenceLib != null)     client.setAgenceLib(agenceLib);
        if (gestionnaire != null)  client.setGestionnaire(gestionnaire);
        if (zoneLib != null)       client.setZoneLib(zoneLib);
        if (dateNaissance != null) client.setDateNaissance(dateNaissance);
    }

    private List<ChangementClient> collecterChangementsClient(
            Client client, Map<String, Object> row, int annee, int mois, LocalDateTime now) {
        List<ChangementClient> liste = new ArrayList<>();
        ajouterSiChange(liste, client, annee, mois, now, "nom",
            client.getNom(), excelParserService.getString(row, "NOM"));
        ajouterSiChange(liste, client, annee, mois, now, "agenceLib",
            client.getAgenceLib(), excelParserService.getString(row, "AGENCELIB"));
        ajouterSiChange(liste, client, annee, mois, now, "gestionnaire",
            client.getGestionnaire(), excelParserService.getString(row, "GESTIONNAIRE"));
        ajouterSiChange(liste, client, annee, mois, now, "zoneLib",
            client.getZoneLib(), excelParserService.getString(row, "ZONELIB"));
        java.time.LocalDate newDate = excelParserService.getDate(row, "DATNAISSANCE");
        if (newDate != null && !newDate.equals(client.getDateNaissance())) {
            liste.add(ChangementClient.builder()
                .client(client).annee(annee).mois(mois)
                .champ("dateNaissance")
                .valeurAvant(client.getDateNaissance() != null ? client.getDateNaissance().toString() : null)
                .valeurApres(newDate.toString())
                .statut(com.securicompte.enums.StatutChangement.EN_ATTENTE)
                .dateDetection(now)
                .build());
        }
        return liste;
    }

    private void ajouterSiChange(List<ChangementClient> liste, Client client,
            int annee, int mois, LocalDateTime now,
            String champ, String ancienne, String nouvelle) {
        if (nouvelle != null && !nouvelle.equals(ancienne)) {
            liste.add(ChangementClient.builder()
                .client(client).annee(annee).mois(mois)
                .champ(champ)
                .valeurAvant(ancienne)
                .valeurApres(nouvelle)
                .statut(com.securicompte.enums.StatutChangement.EN_ATTENTE)
                .dateDetection(now)
                .build());
        }
    }

    private void supprimerDonneesMois(Integer annee, Integer mois, Long importFichierId) {
        impayeRepository.deleteByAnneeAndMois(annee, mois);
        log.info("Suppression bulk impayés {}/{}", mois, annee);
        stockMensuelRepository.deleteBulkByAnneeAndMois(annee, mois);
        log.info("Suppression bulk stock {}/{}", mois, annee);
        souscriptionRepository.deleteByImportFichierId(importFichierId);
        log.info("Suppression bulk des souscriptions de l'import {}/{}", mois, annee);
        changementPrimeRepository.deleteByAnneeAndMois(annee, mois);
        log.info("Suppression changements de prime {}/{}", mois, annee);
        changementClientRepository.deleteByAnneeAndMois(annee, mois);
        log.info("Suppression changements client {}/{}", mois, annee);
    }

    /**
     * Détecte et persiste les changements de prime pour le mois importé.
     * Chaque changement est stocké en base (statut EN_ATTENTE) pour approbation/refus.
     */
    private void detecterChangementsPrimeImport(int annee, int mois, String importePar) {

        List<StockMensuel> stocksCourants = stockMensuelRepository.findByAnneeAndMoisWithClient(annee, mois);
        if (stocksCourants.isEmpty()) return;

        List<Long> clientIds = stocksCourants.stream()
            .map(s -> s.getClient().getId()).distinct().collect(Collectors.toList());

        Map<Long, Souscription> souscriptionParClient = new HashMap<>();
        int batchSize = 1000;
        for (int i = 0; i < clientIds.size(); i += batchSize) {
            List<Long> batch = clientIds.subList(i, Math.min(i + batchSize, clientIds.size()));
            souscriptionRepository.findAllByClientIdsOrderByDateDesc(batch)
                .forEach(s -> souscriptionParClient.putIfAbsent(s.getClient().getId(), s));
        }

        List<ChangementPrime> aCreer = new ArrayList<>();
        List<String> exemples = new ArrayList<>();
        LocalDateTime now = java.time.LocalDateTime.now();

        for (StockMensuel stock : stocksCourants) {
            Souscription souscription = souscriptionParClient.get(stock.getClient().getId());
            if (souscription == null) continue;

            boolean scChange   = !java.util.Objects.equals(souscription.getSecuricompte(), stock.getSecuricompte());
            boolean commChange = !bdEgal(souscription.getCommissions(), stock.getCommissions());

            if (scChange || commChange) {
                aCreer.add(ChangementPrime.builder()
                    .client(stock.getClient())
                    .annee(annee).mois(mois)
                    .securicompteAvant(souscription.getSecuricompte())
                    .securicompteApres(stock.getSecuricompte())
                    .commissionsAvant(souscription.getCommissions())
                    .commissionsApres(stock.getCommissions())
                    .datSouscription(souscription.getDatSouscription())
                    .statut(StatutChangement.EN_ATTENTE)
                    .dateDetection(now)
                    .build());

                if (exemples.size() < 5) {
                    exemples.add(String.format("%s (SC: %s→%s | Com: %s→%s)",
                        stock.getClient().getNom(),
                        nvl(souscription.getSecuricompte()), nvl(stock.getSecuricompte()),
                        nvl(souscription.getCommissions()), nvl(stock.getCommissions())));
                }
            }
        }

        if (!aCreer.isEmpty()) {
            changementPrimeRepository.saveAll(aCreer);
            String details = "Exemples : " + String.join(" | ", exemples)
                + (aCreer.size() > exemples.size()
                    ? String.format(" … et %d autre(s).", aCreer.size() - exemples.size()) : ".");
            notificationService.creerNotificationChangementPrimeImport(
                annee, mois, aCreer.size(), details, importePar);
        }

        log.info("Détection prime {}/{} : {} changement(s) persistés (EN_ATTENTE)", mois, annee, aCreer.size());
    }

    private String nvl(Object o) {
        return o != null ? o.toString() : "N/A";
    }

    private boolean bdEgal(java.math.BigDecimal a, java.math.BigDecimal b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.compareTo(b) == 0;
    }

    public ImportFichier getImportById(Long id) {
        return importFichierRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Import introuvable : " + id));
    }

    /**
     * Marque l'import EN_COURS (synchrone, commité avant le retour au contrôleur)
     * afin que le polling JS détecte la suppression en cours au rechargement de page.
     */
    @Transactional
    public String preparerSuppression(Long importId) {
        ImportFichier imp = importFichierRepository.findById(importId)
            .orElseThrow(() -> new IllegalArgumentException("Import introuvable : " + importId));
        String periode = imp.getMois() + "/" + imp.getAnnee();
        imp.setStatut(StatutImport.EN_COURS);
        importFichierRepository.save(imp);
        return periode;
    }

    /**
     * Suppression asynchrone d'un import et de toutes ses données associées.
     * Utilise TransactionTemplate pour contourner la limitation de self-invocation
     * Spring AOP (appel this.method() bypass le proxy → @Transactional ignoré).
     * Ordre FK : impayés → stock → souscriptions → import_fichier.
     */
    @Async
    public void supprimerImportAsync(Long importId) {
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                ImportFichier imp = importFichierRepository.findById(importId)
                    .orElseThrow(() -> new IllegalArgumentException("Import introuvable : " + importId));
                int annee = imp.getAnnee();
                int mois  = imp.getMois();
                impayeRepository.deleteByAnneeAndMois(annee, mois);
                stockMensuelRepository.deleteBulkByAnneeAndMois(annee, mois);
                souscriptionRepository.deleteByImportFichierId(importId);
                changementPrimeRepository.deleteByAnneeAndMois(annee, mois);
                changementClientRepository.deleteByAnneeAndMois(annee, mois);
                importFichierRepository.deleteDirectById(importId);
                int orphelins = clientRepository.deleteOrphanClients();
                log.info("Import {}/{} supprimé (id={}) — {} client(s) orphelin(s) supprimé(s)", mois, annee, importId, orphelins);
                return null;
            });
        } catch (Exception e) {
            log.error("Erreur suppression asynchrone import {}: {}", importId, e.getMessage(), e);
        }
    }

    public List<ImportFichier> getTousLesImports() {
        return importFichierRepository.findAllByOrderByAnneeDescMoisDesc();
    }

    public List<ImportFichier> getImportsByAnnee(Integer annee) {
        return importFichierRepository.findByAnneeOrderByMoisDesc(annee);
    }

    public long countImportsEnCours() {
        return importFichierRepository.countByStatut(StatutImport.EN_COURS);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.Map<String, Long> getNbPrimeEnAttenteParMois() {
        java.util.Map<String, Long> map = new java.util.HashMap<>();
        changementPrimeRepository.countByStatutGroupedByMois(StatutChangement.EN_ATTENTE)
            .forEach(r -> map.put(r[0] + "-" + r[1], (Long) r[2]));
        return map;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.Map<String, Long> getNbChangementsClientParMois() {
        java.util.Map<String, Long> map = new java.util.HashMap<>();
        changementClientRepository.countGroupedByMois()
            .forEach(r -> map.put(r[0] + "-" + r[1], (Long) r[2]));
        return map;
    }

    @org.springframework.transaction.annotation.Transactional
    public int purgerClientsOrphelins() {
        return clientRepository.deleteOrphanClients();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<ChangementPrimeDto> getChangementsPrime(int annee, int mois) {
        return changementPrimeRepository.findByAnneeAndMoisWithClient(annee, mois)
            .stream().map(this::toChangementDto).collect(Collectors.toList());
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<ChangementPrimeDto> getChangementsEnAttente() {
        return changementPrimeRepository.findByStatutWithClient(StatutChangement.EN_ATTENTE)
            .stream().map(this::toChangementDto).collect(Collectors.toList());
    }

    @org.springframework.transaction.annotation.Transactional
    public void approuverChangement(Long id, String username) {
        ChangementPrime c = changementPrimeRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Changement introuvable : " + id));
        if (c.getStatut() != StatutChangement.EN_ATTENTE) return;

        List<Souscription> sousc = souscriptionRepository.findByClientIdOrderByDatSouscriptionAsc(c.getClient().getId());
        if (!sousc.isEmpty()) {
            Souscription derniere = sousc.get(sousc.size() - 1);
            derniere.setSecuricompte(c.getSecuricompteApres());
            derniere.setCommissions(c.getCommissionsApres());
            souscriptionRepository.save(derniere);
        }

        c.setStatut(StatutChangement.APPROUVE);
        c.setDateDecision(java.time.LocalDateTime.now());
        c.setDecidePar(username);
        changementPrimeRepository.save(c);
        log.info("Changement prime {} approuvé par {}", id, username);
    }

    @org.springframework.transaction.annotation.Transactional
    public void refuserChangement(Long id, String username) {
        ChangementPrime c = changementPrimeRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Changement introuvable : " + id));
        if (c.getStatut() != StatutChangement.EN_ATTENTE) return;

        c.setStatut(StatutChangement.REFUSE);
        c.setDateDecision(java.time.LocalDateTime.now());
        c.setDecidePar(username);
        changementPrimeRepository.save(c);
        log.info("Changement prime {} refusé par {}", id, username);
    }

    @org.springframework.transaction.annotation.Transactional
    public int approuverTousChangements(int annee, int mois, String username) {
        List<ChangementPrime> enAttente = changementPrimeRepository
            .findByAnneeAndMoisAndStatutWithClient(annee, mois, StatutChangement.EN_ATTENTE);
        enAttente.forEach(c -> approuverChangement(c.getId(), username));
        return enAttente.size();
    }

    @org.springframework.transaction.annotation.Transactional
    public int refuserTousChangements(int annee, int mois, String username) {
        List<ChangementPrime> enAttente = changementPrimeRepository
            .findByAnneeAndMoisAndStatutWithClient(annee, mois, StatutChangement.EN_ATTENTE);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        enAttente.forEach(c -> {
            c.setStatut(StatutChangement.REFUSE);
            c.setDateDecision(now);
            c.setDecidePar(username);
        });
        changementPrimeRepository.saveAll(enAttente);
        log.info("{} changements de prime refusés en bloc pour {}/{}", enAttente.size(), mois, annee);
        return enAttente.size();
    }

    // ─── Fichiers Excel stockés ───────────────────────────────────────────────

    @Async
    public void reimporterDepuisBase(Long importId, User user) {
        ImportFichier imp = importFichierRepository.findById(importId)
            .orElseThrow(() -> new IllegalArgumentException("Import introuvable : " + importId));
        ImportFichierBytes bytes = importFichierBytesRepository.findById(importId)
            .orElseThrow(() -> new IllegalArgumentException("Fichier non stocké en base pour l'import : " + importId));
        log.info("Ré-import depuis base — {}/{} ({})", imp.getMois(), imp.getAnnee(), imp.getNomFichier());
        importerFichierAsync(bytes.getFichierBytes(), imp.getNomFichier(),
            "application/octet-stream", imp.getAnnee(), imp.getMois(), user);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.Optional<ImportFichierBytes> getFichierBytes(Long importId) {
        return importFichierBytesRepository.findById(importId);
    }

    @org.springframework.transaction.annotation.Transactional
    public void supprimerFichierBytes(Long importId) {
        importFichierBytesRepository.deleteById(importId);
        log.info("Fichier bytes supprimé pour l'import {}", importId);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.Set<Long> getIdsAvecFichier() {
        return new java.util.HashSet<>(importFichierBytesRepository.findAllIds());
    }

    // ─── Changements données client ──────────────────────────────────────────

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<ChangementClientDto> getChangementsClient(int annee, int mois) {
        return changementClientRepository.findByAnneeAndMoisWithClient(annee, mois)
            .stream().map(this::toChangementClientDto).collect(Collectors.toList());
    }

    private ChangementClientDto toChangementClientDto(ChangementClient c) {
        Client cl = c.getClient();
        String champLabel = switch (c.getChamp()) {
            case "nom"           -> "Nom";
            case "agenceLib"     -> "Agence";
            case "gestionnaire"  -> "Gestionnaire";
            case "zoneLib"       -> "Zone";
            case "dateNaissance" -> "Date de naissance";
            default              -> c.getChamp();
        };
        return ChangementClientDto.builder()
            .id(c.getId())
            .clientId(cl.getId())
            .numeroClient(cl.getNumeroClient())
            .nomClient(cl.getNom())
            .champ(c.getChamp())
            .champLabel(champLabel)
            .valeurAvant(c.getValeurAvant())
            .valeurApres(c.getValeurApres())
            .dateDetection(c.getDateDetection())
            .build();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private ChangementPrimeDto toChangementDto(ChangementPrime c) {
        Client cl = c.getClient();
        return ChangementPrimeDto.builder()
            .id(c.getId())
            .clientId(cl.getId())
            .numeroClient(cl.getNumeroClient())
            .nomClient(cl.getNom())
            .agenceLib(cl.getAgenceLib())
            .gestionnaire(cl.getGestionnaire())
            .dateSouscription(c.getDatSouscription())
            .securicompteAvant(c.getSecuricompteAvant())
            .securicompteApres(c.getSecuricompteApres())
            .commissionsAvant(c.getCommissionsAvant())
            .commissionsApres(c.getCommissionsApres())
            .statut(c.getStatut())
            .dateDetection(c.getDateDetection())
            .dateDecision(c.getDateDecision())
            .decidePar(c.getDecidePar())
            .build();
    }
}
