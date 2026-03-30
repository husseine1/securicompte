package com.securicompte.service;

import com.securicompte.entity.*;
import com.securicompte.enums.StatutImpaye;
import com.securicompte.enums.StatutImport;
import com.securicompte.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SinistreImportService {

    private final SinistreImportRepository sinistreImportRepository;
    private final ClientRepository         clientRepository;
    private final ImpayeRepository         impayeRepository;
    private final StockMensuelRepository   stockMensuelRepository;
    private final SouscriptionRepository   souscriptionRepository;
    private final ImportFichierRepository  importFichierRepository;

    // ─────────────────────────────────────────────────────────────
    //  Import asynchrone
    // ─────────────────────────────────────────────────────────────

    @Async
    public void importerFichierAsync(byte[] fileBytes, String filename, String username) {
        importerFichier(fileBytes, filename, username);
    }

    public void importerFichier(byte[] fileBytes, String filename, String username) {
        SinistreImport si = creerEnCours(filename, username);
        try {
            List<String> erreurDetails = new ArrayList<>();
            int[] result = traiterFichier(fileBytes, si.getId(), erreurDetails);
            String messageErreur = erreurDetails.isEmpty() ? null : String.join("\n", erreurDetails);
            finaliserSucces(si.getId(), result[0], result[1], result[2], messageErreur);
        } catch (Exception e) {
            log.error("Erreur import sinistres '{}': {}", filename, e.getMessage(), e);
            finaliserEchec(si.getId(), e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SinistreImport creerEnCours(String filename, String username) {
        SinistreImport si = SinistreImport.builder()
            .nomFichier(filename)
            .statut(StatutImport.EN_COURS)
            .importePar(username)
            .build();
        return sinistreImportRepository.save(si);
    }

    @Transactional
    public int[] traiterFichier(byte[] fileBytes, Long importId, List<String> erreurDetails) throws Exception {
        List<Map<String, String>> rows = parseExcel(fileBytes);
        log.info("Import sinistres — {} lignes lues", rows.size());

        int nbSinistres = 0, nbNonTrouves = 0, nbErreurs = 0;

        for (Map<String, String> row : rows) {
            try {
                String numeroClient = row.get("CLIENT");
                if (numeroClient == null || numeroClient.isBlank()) continue;

                Optional<Client> opt = clientRepository.findByNumeroClient(numeroClient.trim());
                if (opt.isEmpty()) {
                    nbNonTrouves++;
                    erreurDetails.add("Client introuvable: " + numeroClient);
                    log.debug("Client introuvable: {}", numeroClient);
                    continue;
                }

                String rawDate = row.get("DATE_SINISTRE");
                LocalDate dateSinistre = parseDate(rawDate);
                if (dateSinistre == null) {
                    nbErreurs++;
                    erreurDetails.add("Date invalide pour client " + numeroClient
                        + ": \"" + (rawDate != null ? rawDate : "(vide)") + "\"");
                    log.warn("Date sinistre invalide pour client {}: {}", numeroClient, rawDate);
                    continue;
                }

                Client client = opt.get();
                client.setDateSinistre(dateSinistre);
                clientRepository.save(client);

                // Supprimer les IMPAYÉ existants à partir de la date de sinistre
                impayeRepository.deleteImpaYesFromPeriode(
                    client.getId(), dateSinistre.getYear(), dateSinistre.getMonthValue());

                nbSinistres++;
            } catch (Exception e) {
                nbErreurs++;
                String numCli = row.getOrDefault("CLIENT", "?");
                erreurDetails.add("Erreur pour client " + numCli + ": " + e.getMessage());
                log.warn("Erreur traitement ligne sinistre: {}", e.getMessage());
            }
        }

        log.info("Import sinistres terminé — {} marqués, {} introuvables, {} erreurs",
                 nbSinistres, nbNonTrouves, nbErreurs);
        return new int[]{nbSinistres, nbNonTrouves, nbErreurs};
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finaliserSucces(Long id, int nbSinistres, int nbNonTrouves, int nbErreurs, String messageErreur) {
        SinistreImport si = sinistreImportRepository.findById(id).orElseThrow();
        si.setStatut(StatutImport.SUCCES);
        si.setNbSinistres(nbSinistres);
        si.setNbNonTrouves(nbNonTrouves);
        si.setNbErreurs(nbErreurs);
        si.setMessageErreur(messageErreur);
        si.setDateFinImport(LocalDateTime.now());
        sinistreImportRepository.save(si);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finaliserEchec(Long id, String message) {
        SinistreImport si = sinistreImportRepository.findById(id).orElseThrow();
        si.setStatut(StatutImport.ECHEC);
        si.setMessageErreur(message);
        si.setDateFinImport(LocalDateTime.now());
        sinistreImportRepository.save(si);
    }

    // ─────────────────────────────────────────────────────────────
    //  Annulation sinistre (admin uniquement — cas d'erreur)
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public void annulerSinistre(Long clientId) {
        Client client = clientRepository.findById(clientId)
            .orElseThrow(() -> new RuntimeException("Client introuvable: " + clientId));

        LocalDate ancienneDate = client.getDateSinistre();
        if (ancienneDate == null) return;

        client.setDateSinistre(null);
        clientRepository.save(client);

        // Re-détecter les impayés pour les mois importés >= ancienneDate
        List<ImportFichier> moisConcernes = importFichierRepository.findSuccessFromPeriode(
            ancienneDate.getYear(), ancienneDate.getMonthValue());

        LocalDate dateSouscription = souscriptionRepository.findByClientId(clientId).stream()
            .map(Souscription::getDatSouscription)
            .filter(Objects::nonNull)
            .min(LocalDate::compareTo)
            .orElse(null);

        LocalDateTime now = LocalDateTime.now();
        for (ImportFichier imp : moisConcernes) {
            if (dateSouscription != null) {
                LocalDate debutMois = LocalDate.of(imp.getAnnee(), imp.getMois(), 1);
                if (debutMois.isBefore(dateSouscription)) continue;
            }
            boolean inStock = stockMensuelRepository.existsByClientIdAndAnneeAndMois(
                clientId, imp.getAnnee(), imp.getMois());
            boolean impayeExiste = impayeRepository.existsByClientIdAndAnneeAndMois(
                clientId, imp.getAnnee(), imp.getMois());

            if (!inStock && !impayeExiste) {
                impayeRepository.save(Impaye.builder()
                    .client(client)
                    .annee(imp.getAnnee())
                    .mois(imp.getMois())
                    .statut(StatutImpaye.IMPAYE)
                    .dateDetection(now)
                    .agenceLib(client.getAgenceLib())
                    .gestionnaire(client.getGestionnaire())
                    .zoneLib(client.getZoneLib())
                    .build());
            }
        }

        log.info("Sinistre annulé pour client {} (ancienne date: {}), {} mois recalculés",
                 clientId, ancienneDate, moisConcernes.size());
    }

    // ─────────────────────────────────────────────────────────────
    //  Queries
    // ─────────────────────────────────────────────────────────────

    public SinistreImport getById(Long id) {
        return sinistreImportRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Import sinistre introuvable : " + id));
    }

    @Transactional
    public void supprimerImport(Long id) {
        SinistreImport imp = getById(id);
        if (imp.getStatut() == StatutImport.EN_COURS) {
            throw new IllegalStateException("Impossible de supprimer un import en cours.");
        }
        sinistreImportRepository.deleteById(id);
        log.info("Import sinistre {} supprimé ({})", id, imp.getNomFichier());
    }

    public List<SinistreImport> getTousLesImports() {
        return sinistreImportRepository.findAllByOrderByDateImportDesc();
    }

    public long countImportsEnCours() {
        return sinistreImportRepository.countByStatut(StatutImport.EN_COURS);
    }

    // ─────────────────────────────────────────────────────────────
    //  Parsing Excel
    // ─────────────────────────────────────────────────────────────

    private List<Map<String, String>> parseExcel(byte[] fileBytes) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) throw new IllegalArgumentException("Le fichier Excel est vide.");

            // Rechercher la ligne d'en-tête (parmi les 10 premières)
            int headerRow = -1;
            List<String> headers = new ArrayList<>();

            for (int i = 0; i <= Math.min(10, sheet.getLastRowNum()); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                List<String> candidats = new ArrayList<>();
                for (Cell cell : row) {
                    String v = cellToString(cell);
                    candidats.add(v != null ? v.trim().toUpperCase().replace(" ", "_") : "");
                }
                if (candidats.contains("CLIENT") && candidats.contains("DATE_SINISTRE")) {
                    headers.addAll(candidats);
                    headerRow = i;
                    break;
                }
            }

            if (headerRow == -1)
                throw new IllegalArgumentException(
                    "Colonnes 'CLIENT' et 'DATE_SINISTRE' introuvables. " +
                    "Vérifiez les noms de colonnes dans le fichier.");

            // Lire les données
            for (int i = headerRow + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Map<String, String> rowData = new LinkedHashMap<>();
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.getCell(j, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String val = cellToString(cell);
                    if (val != null && !val.isBlank())
                        rowData.put(headers.get(j), val.trim());
                }
                if (rowData.containsKey("CLIENT"))
                    rows.add(rowData);
            }
        }
        return rows;
    }

    private String cellToString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDate ld = cell.getLocalDateTimeCellValue().toLocalDate();
                    yield ld.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                }
                double d = cell.getNumericCellValue();
                yield (d == Math.floor(d) && !Double.isInfinite(d))
                    ? String.valueOf((long) d) : String.valueOf(d);
            }
            default -> null;
        };
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        for (DateTimeFormatter fmt : List.of(
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ISO_LOCAL_DATE)) {
            try { return LocalDate.parse(s.trim(), fmt); }
            catch (DateTimeParseException ignored) {}
        }
        // Numéro de série Excel (ex: 44806 → 02/09/2022)
        try {
            double serial = Double.parseDouble(s.trim());
            if (serial > 0) {
                return DateUtil.getLocalDateTime(serial).toLocalDate();
            }
        } catch (NumberFormatException ignored) {}
        return null;
    }
}
