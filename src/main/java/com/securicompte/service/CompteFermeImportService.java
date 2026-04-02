package com.securicompte.service;

import com.securicompte.entity.*;
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
public class CompteFermeImportService {

    private final CompteFermeImportRepository compteFermeImportRepository;
    private final ClientRepository            clientRepository;
    private final ImpayeRepository            impayeRepository;

    @Async
    public void importerFichierAsync(byte[] fileBytes, String filename, String username) {
        importerFichier(fileBytes, filename, username);
    }

    public void importerFichier(byte[] fileBytes, String filename, String username) {
        CompteFermeImport cfi = creerEnCours(filename, username);
        try {
            List<String> erreurDetails = new ArrayList<>();
            int[] result = traiterFichier(fileBytes, erreurDetails);
            String messageErreur = erreurDetails.isEmpty() ? null : String.join("\n", erreurDetails);
            finaliserSucces(cfi.getId(), result[0], result[1], result[2], messageErreur);
        } catch (Exception e) {
            log.error("Erreur import comptes fermés '{}': {}", filename, e.getMessage(), e);
            finaliserEchec(cfi.getId(), e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompteFermeImport creerEnCours(String filename, String username) {
        CompteFermeImport cfi = CompteFermeImport.builder()
            .nomFichier(filename)
            .statut(StatutImport.EN_COURS)
            .importePar(username)
            .build();
        return compteFermeImportRepository.save(cfi);
    }

    @Transactional
    public int[] traiterFichier(byte[] fileBytes, List<String> erreurDetails) throws Exception {
        List<Map<String, String>> rows = parseExcel(fileBytes);
        log.info("Import comptes fermés — {} lignes lues", rows.size());

        int nbFermes = 0, nbNonTrouves = 0, nbErreurs = 0;

        for (Map<String, String> row : rows) {
            try {
                String numeroClient = row.get("CLIENT");
                if (numeroClient == null || numeroClient.isBlank()) continue;

                Optional<Client> opt = clientRepository.findByNumeroClient(numeroClient.trim());
                if (opt.isEmpty()) {
                    nbNonTrouves++;
                    erreurDetails.add("Client introuvable: " + numeroClient);
                    continue;
                }

                String rawDate = row.get("DATE_FERMETURE");
                LocalDate dateFermeture = parseDate(rawDate);
                if (dateFermeture == null) {
                    nbErreurs++;
                    erreurDetails.add("Date invalide pour client " + numeroClient
                        + ": \"" + (rawDate != null ? rawDate : "(vide)") + "\"");
                    continue;
                }

                Client client = opt.get();
                client.setDateCompteFerme(dateFermeture);
                clientRepository.save(client);

                impayeRepository.deleteImpaYesFromPeriode(
                    client.getId(), dateFermeture.getYear(), dateFermeture.getMonthValue());

                nbFermes++;
            } catch (Exception e) {
                nbErreurs++;
                String numCli = row.getOrDefault("CLIENT", "?");
                erreurDetails.add("Erreur pour client " + numCli + ": " + e.getMessage());
                log.warn("Erreur traitement ligne fermeture: {}", e.getMessage());
            }
        }

        log.info("Import comptes fermés terminé — {} fermés, {} introuvables, {} erreurs",
                 nbFermes, nbNonTrouves, nbErreurs);
        return new int[]{nbFermes, nbNonTrouves, nbErreurs};
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finaliserSucces(Long id, int nbFermes, int nbNonTrouves, int nbErreurs, String messageErreur) {
        CompteFermeImport cfi = compteFermeImportRepository.findById(id).orElseThrow();
        cfi.setStatut(StatutImport.SUCCES);
        cfi.setNbFermes(nbFermes);
        cfi.setNbNonTrouves(nbNonTrouves);
        cfi.setNbErreurs(nbErreurs);
        cfi.setMessageErreur(messageErreur);
        cfi.setDateFinImport(LocalDateTime.now());
        compteFermeImportRepository.save(cfi);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finaliserEchec(Long id, String message) {
        CompteFermeImport cfi = compteFermeImportRepository.findById(id).orElseThrow();
        cfi.setStatut(StatutImport.ECHEC);
        cfi.setMessageErreur(message);
        cfi.setDateFinImport(LocalDateTime.now());
        compteFermeImportRepository.save(cfi);
    }

    @Transactional
    public void annulerFermeture(Long clientId) {
        Client client = clientRepository.findById(clientId)
            .orElseThrow(() -> new RuntimeException("Client introuvable: " + clientId));
        client.setDateCompteFerme(null);
        clientRepository.save(client);
        log.info("Fermeture compte annulée pour client {}", clientId);
    }

    public CompteFermeImport getById(Long id) {
        return compteFermeImportRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Import compte fermé introuvable: " + id));
    }

    @Transactional
    public void supprimerImport(Long id) {
        CompteFermeImport cfi = getById(id);
        if (cfi.getStatut() == StatutImport.EN_COURS) {
            throw new IllegalStateException("Impossible de supprimer un import en cours.");
        }
        compteFermeImportRepository.deleteById(id);
        log.info("Import compte fermé {} supprimé ({})", id, cfi.getNomFichier());
    }

    public List<CompteFermeImport> getTousLesImports() {
        return compteFermeImportRepository.findAllByOrderByDateImportDesc();
    }

    public long countImportsEnCours() {
        return compteFermeImportRepository.countByStatut(StatutImport.EN_COURS);
    }

    private List<Map<String, String>> parseExcel(byte[] fileBytes) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) throw new IllegalArgumentException("Le fichier Excel est vide.");

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
                if (candidats.contains("CLIENT") && candidats.contains("DATE_FERMETURE")) {
                    headers.addAll(candidats);
                    headerRow = i;
                    break;
                }
            }

            if (headerRow == -1)
                throw new IllegalArgumentException(
                    "Colonnes 'CLIENT' et 'DATE_FERMETURE' introuvables dans le fichier.");

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
        try {
            double serial = Double.parseDouble(s.trim());
            if (serial > 0) return DateUtil.getLocalDateTime(serial).toLocalDate();
        } catch (NumberFormatException ignored) {}
        return null;
    }
}
