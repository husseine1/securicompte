package com.securicompte.service;

import com.securicompte.entity.*;
import com.securicompte.enums.TypeSouscription;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@Slf4j
public class ExcelParserService {

    private static final List<String> SHEETS_NOUVELLE =
        List.of("nouvelle souscription", "nouvelles souscriptions", "nouvelle");
    private static final List<String> SHEETS_ANCIENNE =
        List.of("ancienne souscription", "anciennes souscriptions", "ancienne");
    private static final List<String> SHEETS_STOCK =
        List.of("stock du mois", "stock", "stock mois");

    public record ExcelData(
        List<Map<String, Object>> nouvelles,
        List<Map<String, Object>> anciennes,
        List<Map<String, Object>> stock
    ) {}

    /**
     * Parse le fichier Excel et retourne les données des 3 feuilles.
     */
    public ExcelData parseExcel(MultipartFile file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheetNouvelle = findSheet(workbook, SHEETS_NOUVELLE);
            Sheet sheetAncienne = findSheet(workbook, SHEETS_ANCIENNE);
            Sheet sheetStock    = findSheet(workbook, SHEETS_STOCK);

            if (sheetNouvelle == null)
                throw new IllegalArgumentException("Feuille 'Nouvelle souscription' introuvable dans le fichier.");
            if (sheetAncienne == null)
                throw new IllegalArgumentException("Feuille 'Ancienne souscription' introuvable dans le fichier.");
            if (sheetStock == null)
                throw new IllegalArgumentException("Feuille 'Stock du mois' introuvable dans le fichier.");

            List<Map<String, Object>> nouvelles = parseSheet(sheetNouvelle);
            List<Map<String, Object>> anciennes = parseSheet(sheetAncienne);
            List<Map<String, Object>> stock     = parseSheet(sheetStock);

            log.info("Parsing Excel OK — Nouvelles: {}, Anciennes: {}, Stock: {}",
                nouvelles.size(), anciennes.size(), stock.size());

            return new ExcelData(nouvelles, anciennes, stock);
        }
    }

    /**
     * Parse une feuille et retourne une liste de Map<colonne, valeur>.
     */
    private List<Map<String, Object>> parseSheet(Sheet sheet) {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> headers = new ArrayList<>();
        boolean firstRow = true;

        for (Row row : sheet) {
            if (firstRow) {
                // Ligne d'en-tête → mémoriser les noms de colonnes
                for (Cell cell : row) {
                    String header = getCellStringValue(cell);
                    headers.add(header != null ? header.trim().toUpperCase() : "COL_" + cell.getColumnIndex());
                }
                firstRow = false;
                continue;
            }
            if (isRowEmpty(row)) continue;

            Map<String, Object> rowData = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                rowData.put(headers.get(i), parseCellValue(cell));
            }

            // Ne garder que si CLIENT n'est pas vide
            Object clientVal = rowData.get("CLIENT");
            if (clientVal != null && !clientVal.toString().isBlank()) {
                rows.add(rowData);
            }
        }
        return rows;
    }

    // ────────────────────────────────────────────────────────────────
    // Conversions Excel → Entités
    // ────────────────────────────────────────────────────────────────

    public Client rowToClient(Map<String, Object> row) {
        return Client.builder()
            .numeroClient(getString(row, "CLIENT"))
            .nom(getString(row, "NOM"))
            .compte(getString(row, "COMPTE"))
            .zoneLib(getString(row, "ZONELIB"))
            .agenceLib(getString(row, "AGENCELIB"))
            .gestionnaire(getString(row, "GESTIONNAIRE"))
            .dateNaissance(getDate(row, "DATNAISSANCE"))
            .actif(true)
            .build();
    }

    public Souscription rowToSouscription(Map<String, Object> row, Client client,
                                           TypeSouscription type, ImportFichier importFichier) {
        LocalDate datSouscription = getDate(row, "DATSOUSCRIPTION");
        if (datSouscription == null) {
            datSouscription = LocalDate.of(importFichier.getAnnee(), importFichier.getMois(), 1);
        }
        return Souscription.builder()
            .client(client)
            .securicompte(getString(row, "SECURICOMPTE"))
            .commissions(getDecimal(row, "COMMISSIONS"))
            .libelPackage(getString(row, "LIBELL_PACKAGE"))
            .optionSecuricompte(getString(row, "OPTION SECURICOMPTE"))
            .datSouscription(datSouscription)
            .datOuverture(getDate(row, "DATOUV"))
            .typeSouscription(type)
            .importFichier(importFichier)
            .build();
    }

    public StockMensuel rowToStock(Map<String, Object> row, Client client,
                                    int annee, int mois, ImportFichier importFichier) {
        return StockMensuel.builder()
            .client(client)
            .annee(annee)
            .mois(mois)
            .securicompte(getString(row, "SECURICOMPTE"))
            .commissions(getDecimal(row, "COMMISSIONS"))
            .libelPackage(getString(row, "LIBELL_PACKAGE"))
            .optionSecuricompte(getString(row, "OPTION SECURICOMPTE"))
            .datSouscription(getDate(row, "DATSOUSCRIPTION"))
            .zoneLib(getString(row, "ZONELIB"))
            .agenceLib(getString(row, "AGENCELIB"))
            .gestionnaire(getString(row, "GESTIONNAIRE"))
            .importFichier(importFichier)
            .build();
    }

    // ────────────────────────────────────────────────────────────────
    // Helpers d'extraction
    // ────────────────────────────────────────────────────────────────

    public String getString(Map<String, Object> row, String key) {
        Object val = row.get(key.toUpperCase());
        if (val == null) return null;
        String s = val.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private LocalDate getDate(Map<String, Object> row, String key) {
        Object val = row.get(key.toUpperCase());
        if (val instanceof LocalDate ld) return ld;
        if (val instanceof String s && !s.isBlank()) {
            try {
                String[] parts = s.trim().split("/");
                if (parts.length == 3)
                    return LocalDate.of(Integer.parseInt(parts[2]),
                                        Integer.parseInt(parts[1]),
                                        Integer.parseInt(parts[0]));
                return LocalDate.parse(s.trim());
            } catch (Exception ignored) {}
        }
        return null;
    }

    private BigDecimal getDecimal(Map<String, Object> row, String key) {
        Object val = row.get(key.toUpperCase());
        if (val == null) return null;
        try {
            if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
            String s = val.toString().replace(",", ".").trim();
            return s.isEmpty() ? null : new BigDecimal(s);
        } catch (Exception e) { return null; }
    }

    private Object parseCellValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate();
                }
                double d = cell.getNumericCellValue();
                yield (d == Math.floor(d) && !Double.isInfinite(d))
                    ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> cell.getBooleanCellValue();
            case FORMULA -> {
                try { yield cell.getStringCellValue(); }
                catch (Exception e) { yield cell.getNumericCellValue(); }
            }
            default -> null;
        };
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> null;
        };
    }

    private Sheet findSheet(Workbook wb, List<String> names) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            String sheetName = wb.getSheetName(i).toLowerCase().trim();
            if (names.contains(sheetName)) return wb.getSheetAt(i);
        }
        return null;
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK
                && !cell.toString().trim().isEmpty()) return false;
        }
        return true;
    }
}
