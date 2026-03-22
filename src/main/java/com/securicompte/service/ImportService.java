package com.securicompte.service;

import com.securicompte.dto.ImportResultDto;
import com.securicompte.entity.*;
import com.securicompte.enums.StatutImport;
import com.securicompte.enums.TypeSouscription;
import com.securicompte.repository.*;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
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
            List<Client> saved = clientRepository.saveAll(nouveaux);
            saved.forEach(c -> cache.put(c.getNumeroClient(), c));
            log.info("{} nouveau(x) client(s) créé(s)", saved.size());
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
        stockMensuelRepository.deleteBulkByAnneeAndMois(annee, mois);
        log.info("Suppression bulk stock {}/{}", mois, annee);
        souscriptionRepository.deleteByImportFichierId(importFichierId);
        log.info("Suppression bulk des souscriptions de l'import {}/{}", mois, annee);
    }

    /**
     * Compare la prime (securicompte + commissions) du stock importé ce mois
     * avec la prime du stock au mois de souscription de chaque client.
     *
     * Logique :
     *  1. Charger le stock du mois importé (M) → prime courante
     *  2. Charger la souscription la plus récente de chaque client → dat_souscription
     *  3. Regrouper les clients par mois de souscription
     *  4. Pour chaque groupe, charger en bulk le stock du mois de souscription → prime de référence
     *  5. Comparer les deux primes ; si écart → notification de synthèse
     *
     * Fallback : si le stock du mois de souscription n'existe pas en base,
     * comparer avec les champs securicompte/commissions de l'entité Souscription.
     */
    private void detecterChangementsPrimeImport(int annee, int mois, String importePar) {

        // ── Étape 1 : stock du mois importé ──────────────────────────────────────
        List<StockMensuel> stocksCourants = stockMensuelRepository.findByAnneeAndMoisWithClient(annee, mois);
        if (stocksCourants.isEmpty()) return;

        Map<Long, StockMensuel> stockCourantParClient = stocksCourants.stream()
            .collect(Collectors.toMap(s -> s.getClient().getId(), s -> s));

        List<Long> clientIds = new ArrayList<>(stockCourantParClient.keySet());

        // ── Étape 2 : souscription la plus récente de chaque client (bulk) ───────
        Map<Long, Souscription> souscriptionParClient = new HashMap<>();
        int batchSize = 1000;
        for (int i = 0; i < clientIds.size(); i += batchSize) {
            List<Long> batch = clientIds.subList(i, Math.min(i + batchSize, clientIds.size()));
            souscriptionRepository.findAllByClientIdsOrderByDateDesc(batch)
                .forEach(s -> souscriptionParClient.putIfAbsent(s.getClient().getId(), s));
        }

        // ── Étape 3 : regrouper par mois de souscription ─────────────────────────
        // Clé "YYYY_M" → liste de clientIds ayant souscrit ce mois
        Map<String, List<Long>> clientsParMoisSouscription = new LinkedHashMap<>();
        for (Long clientId : clientIds) {
            Souscription s = souscriptionParClient.get(clientId);
            if (s == null || s.getDatSouscription() == null) continue;
            LocalDate d = s.getDatSouscription();
            String cle = d.getYear() + "_" + d.getMonthValue();
            clientsParMoisSouscription.computeIfAbsent(cle, k -> new ArrayList<>()).add(clientId);
        }

        // ── Étape 4 : charger en bulk le stock de référence (mois de souscription) ─
        Map<Long, StockMensuel> stockRefParClient = new HashMap<>();
        for (Map.Entry<String, List<Long>> entry : clientsParMoisSouscription.entrySet()) {
            String[] parts = entry.getKey().split("_");
            int annSousc  = Integer.parseInt(parts[0]);
            int moisSousc = Integer.parseInt(parts[1]);
            if (annSousc == annee && moisSousc == mois) continue; // même mois → rien à comparer

            List<Long> groupe = entry.getValue();
            for (int i = 0; i < groupe.size(); i += batchSize) {
                List<Long> batch = groupe.subList(i, Math.min(i + batchSize, groupe.size()));
                stockMensuelRepository.findByClientIdsAndAnneeAndMois(batch, annSousc, moisSousc)
                    .forEach(s -> stockRefParClient.put(s.getClient().getId(), s));
            }
        }

        // ── Étape 5 : comparer et collecter les écarts ────────────────────────────
        List<String> exemples = new ArrayList<>();
        int nbChangements = 0;

        for (StockMensuel courant : stocksCourants) {
            Long clientId = courant.getClient().getId();
            StockMensuel ref = stockRefParClient.get(clientId);

            String scRef, scCourant, commRef, commCourant;
            if (ref != null) {
                // Comparaison stock-à-stock (référence = mois de souscription)
                scRef      = nvl(ref.getSecuricompte());
                scCourant  = nvl(courant.getSecuricompte());
                commRef    = nvl(ref.getCommissions());
                commCourant= nvl(courant.getCommissions());
            } else {
                // Fallback : champs de l'entité Souscription
                Souscription s = souscriptionParClient.get(clientId);
                if (s == null) continue;
                scRef      = nvl(s.getSecuricompte());
                scCourant  = nvl(courant.getSecuricompte());
                commRef    = nvl(s.getCommissions());
                commCourant= nvl(courant.getCommissions());
            }

            boolean scChange   = !scRef.equals(scCourant);
            boolean commChange = !commRef.equals(commCourant);

            if (scChange || commChange) {
                nbChangements++;
                if (exemples.size() < 5) {
                    exemples.add(String.format("%s (SC: %s→%s | Com: %s→%s)",
                        courant.getClient().getNom(), scRef, scCourant, commRef, commCourant));
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

    @Transactional
    public void supprimerImport(Long importId) {
        ImportFichier imp = importFichierRepository.findById(importId)
            .orElseThrow(() -> new IllegalArgumentException("Import introuvable : " + importId));
        impayeRepository.deleteByAnneeAndMois(imp.getAnnee(), imp.getMois());
        supprimerDonneesMois(imp.getAnnee(), imp.getMois(), importId);
        importFichierRepository.deleteById(importId);
        log.info("Import {}/{} supprimé (id={})", imp.getMois(), imp.getAnnee(), importId);
    }

    public List<ImportFichier> getTousLesImports() {
        return importFichierRepository.findAllByOrderByAnneeDescMoisDesc();
    }

    public List<ImportFichier> getImportsByAnnee(Integer annee) {
        return importFichierRepository.findByAnneeOrderByMoisDesc(annee);
    }
}
