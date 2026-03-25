package com.securicompte.service;

import com.securicompte.dto.ChangementPrimeDto;
import com.securicompte.dto.ImportResultDto;
import com.securicompte.entity.*;
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

    private final ExcelParserService       excelParserService;
    private final ImpayeDetectionService   impayeDetectionService;
    private final NotificationService      notificationService;
    private final ImportFichierRepository  importFichierRepository;
    private final ClientRepository         clientRepository;
    private final SouscriptionRepository   souscriptionRepository;
    private final StockMensuelRepository   stockMensuelRepository;
    private final ImpayeRepository         impayeRepository;
    private final PlatformTransactionManager transactionManager;

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
            importerFichier(file, annee, mois, importePar);
        } catch (Exception e) {
            log.error("Erreur import asynchrone: {}", e.getMessage(), e);
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

            // Tx2 : supprimer l'ancien stock + importer toutes les données (atomique)
            int[] counts = tx.execute(status -> {
                ImportFichier importFichier = importFichierRepository.findById(importId).orElseThrow();
                if (isReimport) {
                    supprimerDonneesMois(annee, mois, importFichier.getId());
                }
                Map<String, Client> cache = construireClientCache(
                    excelData.nouvelles(), excelData.anciennes(), excelData.stock());
                int[] resNn = importerSouscriptionsBulk(
                    excelData.nouvelles(), TypeSouscription.NOUVELLE, importFichier, cache);
                int[] resNa = importerSouscriptionsBulk(
                    excelData.anciennes(), TypeSouscription.ANCIENNE, importFichier, cache);
                int[] resNs = importerStockBulk(
                    excelData.stock(), annee, mois, importFichier, cache);
                int ni = impayeDetectionService.detecterImpaYesDuMois(annee, mois);
                int nbErreurs = resNn[1] + resNa[1] + resNs[1];
                return new int[]{resNn[0], resNa[0], resNs[0], ni, nbErreurs};
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
    private Map<String, Client> construireClientCache(
            List<Map<String, Object>> nouvelles,
            List<Map<String, Object>> anciennes,
            List<Map<String, Object>> stock) {

        // Collecter la première ligne de chaque numéro de client (toutes feuilles)
        Map<String, Map<String, Object>> premiereLignePar = new LinkedHashMap<>();
        for (List<Map<String, Object>> feuille : List.of(nouvelles, anciennes, stock)) {
            for (Map<String, Object> row : feuille) {
                String num = excelParserService.getString(row, "CLIENT");
                if (num != null) premiereLignePar.putIfAbsent(num, row);
            }
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
        for (Client client : cache.values()) {
            Map<String, Object> row = premiereLignePar.get(client.getNumeroClient());
            if (row != null && clientAChange(client, row)) {
                updateClientInfos(client, row);
                aMettrAJour.add(client);
            }
        }
        if (!aMettrAJour.isEmpty()) {
            clientRepository.saveAll(aMettrAJour);
            log.info("{} client(s) mis à jour (données modifiées)", aMettrAJour.size());
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
     * Importe les souscriptions en bulk (nouvelles ou anciennes).
     * Retourne int[]{nbImportées, nbErreurs}.
     */
    private int[] importerSouscriptionsBulk(List<Map<String, Object>> rows,
                                              TypeSouscription type,
                                              ImportFichier importFichier,
                                              Map<String, Client> clientCache) {
        // Charger uniquement les clés des clients présents dans ce fichier (pas tout l'historique)
        List<Long> candidateIds = rows.stream()
            .map(r -> excelParserService.getString(r, "CLIENT"))
            .filter(Objects::nonNull)
            .map(clientCache::get)
            .filter(Objects::nonNull)
            .map(com.securicompte.entity.Client::getId)
            .distinct()
            .collect(Collectors.toList());

        Set<String> existingKeys = new HashSet<>();
        int batchSizeIds = 5000;
        for (int i = 0; i < candidateIds.size(); i += batchSizeIds) {
            List<Long> batch = candidateIds.subList(i, Math.min(i + batchSizeIds, candidateIds.size()));
            existingKeys.addAll(souscriptionRepository.findExistingKeysForClients(type, batch));
        }

        List<Souscription> toSave = new ArrayList<>();
        int erreurs = 0;
        for (Map<String, Object> row : rows) {
            try {
                String num = excelParserService.getString(row, "CLIENT");
                if (num == null) continue;
                Client client = clientCache.get(num);
                if (client == null) continue;

                Souscription s = excelParserService.rowToSouscription(row, client, type, importFichier);
                String key = client.getId() + "_" + s.getDatSouscription() + "_" + type;
                if (!existingKeys.contains(key)) {
                    toSave.add(s);
                    existingKeys.add(key);
                }
            } catch (Exception e) {
                erreurs++;
                log.warn("Erreur import souscription ligne: {}", e.getMessage());
            }
        }
        souscriptionRepository.saveAll(toSave);
        return new int[]{toSave.size(), erreurs};
    }

    /**
     * Importe le stock mensuel en bulk.
     * Seul l'identifiant client (colonne CLIENT) est accepté — pas de fallback par NOM
     * pour éviter les faux positifs en cas d'homonymes.
     * Retourne int[]{nbImportés, nbErreurs}.
     */
    private int[] importerStockBulk(List<Map<String, Object>> rows, int annee, int mois,
                                     ImportFichier importFichier,
                                     Map<String, Client> clientCache) {
        Set<Long> seen = new HashSet<>();
        List<StockMensuel> toSave = new ArrayList<>();
        int erreurs = 0;
        for (Map<String, Object> row : rows) {
            try {
                String num = excelParserService.getString(row, "CLIENT");
                if (num == null) {
                    log.warn("Ligne stock ignorée : colonne CLIENT absente. Vérifiez que le fichier contient bien la colonne CLIENT.");
                    erreurs++;
                    continue;
                }
                Client client = clientCache.get(num);
                if (client == null) {
                    log.warn("Client introuvable pour le numéro: {}", num);
                    erreurs++;
                    continue;
                }
                if (!seen.contains(client.getId())) {
                    toSave.add(excelParserService.rowToStock(row, client, annee, mois, importFichier));
                    seen.add(client.getId());
                }
            } catch (Exception e) {
                erreurs++;
                log.warn("Erreur import stock ligne: {}", e.getMessage());
            }
        }
        stockMensuelRepository.saveAll(toSave);
        return new int[]{toSave.size(), erreurs};
    }

    private boolean clientAChange(Client client, Map<String, Object> row) {
        String nom         = excelParserService.getString(row, "NOM");
        String agenceLib   = excelParserService.getString(row, "AGENCELIB");
        String gestionnaire = excelParserService.getString(row, "GESTIONNAIRE");
        String zoneLib     = excelParserService.getString(row, "ZONELIB");
        return !Objects.equals(nom, client.getNom())
            || !Objects.equals(agenceLib, client.getAgenceLib())
            || !Objects.equals(gestionnaire, client.getGestionnaire())
            || !Objects.equals(zoneLib, client.getZoneLib());
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

    private void supprimerDonneesMois(Integer annee, Integer mois, Long importFichierId) {
        impayeRepository.deleteByAnneeAndMois(annee, mois);
        log.info("Suppression bulk impayés {}/{}", mois, annee);
        stockMensuelRepository.deleteBulkByAnneeAndMois(annee, mois);
        log.info("Suppression bulk stock {}/{}", mois, annee);
        souscriptionRepository.deleteByImportFichierId(importFichierId);
        log.info("Suppression bulk des souscriptions de l'import {}/{}", mois, annee);
    }

    /**
     * Pour chaque client dans le stock importé ce mois, compare sa prime courante
     * (securicompte + commissions dans StockMensuel) avec sa prime à la date de souscription
     * (securicompte + commissions dans l'entité Souscription).
     * Si un écart est détecté, crée une notification de synthèse.
     */
    private void detecterChangementsPrimeImport(int annee, int mois, String importePar) {

        // Stock du mois importé — avec client déjà chargé (JOIN FETCH, pas de N+1)
        List<StockMensuel> stocksCourants = stockMensuelRepository.findByAnneeAndMoisWithClient(annee, mois);
        if (stocksCourants.isEmpty()) return;

        // Souscription la plus récente de chaque client (bulk, par lots de 1000)
        List<Long> clientIds = stocksCourants.stream()
            .map(s -> s.getClient().getId())
            .distinct()
            .collect(Collectors.toList());

        Map<Long, Souscription> souscriptionParClient = new HashMap<>();
        int batchSize = 1000;
        for (int i = 0; i < clientIds.size(); i += batchSize) {
            List<Long> batch = clientIds.subList(i, Math.min(i + batchSize, clientIds.size()));
            souscriptionRepository.findAllByClientIdsOrderByDateDesc(batch)
                .forEach(s -> souscriptionParClient.putIfAbsent(s.getClient().getId(), s));
        }

        // Comparaison : prime du stock importé vs prime à la date de souscription
        List<String> exemples = new ArrayList<>();
        int nbChangements = 0;

        for (StockMensuel stock : stocksCourants) {
            Souscription souscription = souscriptionParClient.get(stock.getClient().getId());
            if (souscription == null) continue;

            String scSousc   = nvl(souscription.getSecuricompte());
            String scStock   = nvl(stock.getSecuricompte());
            String commSousc = nvl(souscription.getCommissions());
            String commStock = nvl(stock.getCommissions());

            boolean scChange   = !scSousc.equals(scStock);
            boolean commChange = !commSousc.equals(commStock);

            if (scChange || commChange) {
                nbChangements++;
                if (exemples.size() < 5) {
                    exemples.add(String.format("%s (SC: %s→%s | Com: %s→%s)",
                        stock.getClient().getNom(),
                        scSousc, scStock, commSousc, commStock));
                }
            }
        }

        if (nbChangements > 0) {
            String details = "Exemples : " + String.join(" | ", exemples)
                + (nbChangements > exemples.size()
                    ? String.format(" … et %d autre(s).", nbChangements - exemples.size()) : ".");
            notificationService.creerNotificationChangementPrimeImport(
                annee, mois, nbChangements, details, importePar);
        }

        log.info("Détection prime import {}/{} : {} changement(s) sur {} clients analysés",
            mois, annee, nbChangements, stocksCourants.size());
    }

    private String nvl(Object o) {
        return o != null ? o.toString() : "N/A";
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
                importFichierRepository.deleteDirectById(importId);
                log.info("Import {}/{} supprimé (id={})", mois, annee, importId);
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

    /**
     * Relit la comparaison stock importé vs souscription pour un mois donné
     * et retourne la liste complète des clients avec écart de prime.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<ChangementPrimeDto> getChangementsPrime(int annee, int mois) {
        List<StockMensuel> stocks = stockMensuelRepository.findByAnneeAndMoisWithClient(annee, mois);
        if (stocks.isEmpty()) return List.of();

        List<Long> clientIds = stocks.stream()
            .map(s -> s.getClient().getId())
            .distinct()
            .collect(Collectors.toList());

        Map<Long, Souscription> souscriptionParClient = new HashMap<>();
        int batchSize = 1000;
        for (int i = 0; i < clientIds.size(); i += batchSize) {
            List<Long> batch = clientIds.subList(i, Math.min(i + batchSize, clientIds.size()));
            souscriptionRepository.findAllByClientIdsOrderByDateDesc(batch)
                .forEach(s -> souscriptionParClient.putIfAbsent(s.getClient().getId(), s));
        }

        List<ChangementPrimeDto> result = new ArrayList<>();
        for (StockMensuel stock : stocks) {
            Souscription souscription = souscriptionParClient.get(stock.getClient().getId());
            if (souscription == null) continue;

            String scAvant   = nvl(souscription.getSecuricompte());
            String scApres   = nvl(stock.getSecuricompte());
            String commAvant = nvl(souscription.getCommissions());
            String commApres = nvl(stock.getCommissions());

            if (!scAvant.equals(scApres) || !commAvant.equals(commApres)) {
                result.add(ChangementPrimeDto.builder()
                    .clientId(stock.getClient().getId())
                    .numeroClient(stock.getClient().getNumeroClient())
                    .nomClient(stock.getClient().getNom())
                    .agenceLib(stock.getClient().getAgenceLib())
                    .gestionnaire(stock.getClient().getGestionnaire())
                    .dateSouscription(souscription.getDatSouscription())
                    .securicompteAvant(souscription.getSecuricompte())
                    .securicompteApres(stock.getSecuricompte())
                    .commissionsAvant(souscription.getCommissions())
                    .commissionsApres(stock.getCommissions())
                    .build());
            }
        }
        return result;
    }
}
