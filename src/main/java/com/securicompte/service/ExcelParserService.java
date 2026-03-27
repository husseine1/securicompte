package com.securicompte.service;

import com.securicompte.entity.*;
import com.securicompte.enums.TypeSouscription;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.binary.XSSFBSharedStringsTable;
import org.apache.poi.xssf.binary.XSSFBSheetHandler;
import org.apache.poi.xssf.binary.XSSFBStylesTable;
import org.apache.poi.xssf.eventusermodel.XSSFBReader;
import org.apache.poi.xssf.usermodel.XSSFComment;
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

    // Mots-clés de recherche (correspondance partielle insensible à la casse)
    private static final List<String> SHEETS_NOUVELLE =
        List.of("nouvelle souscription", "nouvelles souscriptions", "nouvelle", "nv souscription", "nv");
    private static final List<String> SHEETS_ANCIENNE =
        List.of("ancienne souscription", "anciennes souscriptions", "ancienne", "anc souscription", "anc");
    private static final List<String> SHEETS_STOCK =
        List.of("stock du mois", "stock mois", "stock mensuel", "stock");

    public record ExcelData(
        List<Map<String, Object>> nouvelles,
        List<Map<String, Object>> anciennes,
        List<Map<String, Object>> stock
    ) {}

    /**
     * Parse le fichier Excel et retourne les données des 3 feuilles.
     */
    public ExcelData parseExcel(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase().endsWith(".xlsb")) {
            return parseXlsb(file);
        }
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheetNouvelle = findSheet(workbook, SHEETS_NOUVELLE);
            Sheet sheetAncienne = findSheet(workbook, SHEETS_ANCIENNE);
            Sheet sheetStock    = findSheet(workbook, SHEETS_STOCK);

            List<String> sheetsFound = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) sheetsFound.add(workbook.getSheetName(i));

            if (sheetNouvelle == null)
                throw new IllegalArgumentException("Feuille 'Nouvelle souscription' introuvable. Feuilles présentes : " + sheetsFound);
            if (sheetAncienne == null)
                throw new IllegalArgumentException("Feuille 'Ancienne souscription' introuvable. Feuilles présentes : " + sheetsFound);
            if (sheetStock == null)
                throw new IllegalArgumentException("Feuille 'Stock du mois' introuvable. Feuilles présentes : " + sheetsFound);

            List<Map<String, Object>> nouvelles = parseSheet(sheetNouvelle, false);
            List<Map<String, Object>> anciennes = parseSheet(sheetAncienne, false);
            List<Map<String, Object>> stock     = parseSheet(sheetStock, true);

            log.info("Parsing Excel OK — Nouvelles: {}, Anciennes: {}, Stock: {}",
                nouvelles.size(), anciennes.size(), stock.size());

            return new ExcelData(nouvelles, anciennes, stock);
        }
    }

    private ExcelData parseXlsb(MultipartFile file) throws IOException {
        // Augmenter la limite POI pour les grands fichiers xlsb
        IOUtils.setByteArrayMaxOverride(300_000_000);
        Map<String, List<Map<String, Object>>> sheetsData = new LinkedHashMap<>();
        try (OPCPackage pkg = OPCPackage.open(file.getInputStream())) {
            XSSFBReader reader;
            try {
                reader = new XSSFBReader(pkg);
            } catch (org.apache.poi.openxml4j.exceptions.OpenXML4JException e) {
                throw new IOException("Impossible de lire le fichier .xlsb", e);
            }
            // Utiliser XSSFBSharedStringsTable (format binaire xlsb) au lieu de getSharedStringsTable()
            org.apache.poi.xssf.model.SharedStrings sst;
            try {
                sst = new XSSFBSharedStringsTable(pkg);
            } catch (Exception e) {
                log.warn("Impossible de lire la table des chaînes xlsb: {}", e.getMessage());
                sst = null;
            }
            XSSFBStylesTable styles = reader.getXSSFBStylesTable();
            XSSFBReader.SheetIterator iter = (XSSFBReader.SheetIterator) reader.getSheetsData();
            while (iter.hasNext()) {
                try (java.io.InputStream sheetStream = iter.next()) {
                    String sheetName = iter.getSheetName().toLowerCase().trim();
                    boolean isStock = SHEETS_STOCK.stream().anyMatch(sheetName::contains);
                    XlsbRowCollector collector = new XlsbRowCollector(isStock);
                    XSSFBSheetHandler handler = new XSSFBSheetHandler(
                        sheetStream, styles, iter.getXSSFBSheetComments(),
                        sst, collector, new DataFormatter(), false);
                    handler.parse();
                    log.info("Feuille '{}' — colonnes: {} — {} lignes valides",
                        sheetName, collector.getHeaders(), collector.getRows().size());
                    sheetsData.put(sheetName, collector.getRows());
                }
            }
            log.info("Feuilles trouvées dans le fichier xlsb: {}", sheetsData.keySet());
        } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            throw new IOException("Fichier .xlsb invalide ou corrompu", e);
        }

        List<Map<String, Object>> nouvelles = findSheetRows(sheetsData, SHEETS_NOUVELLE);
        List<Map<String, Object>> anciennes = findSheetRows(sheetsData, SHEETS_ANCIENNE);
        List<Map<String, Object>> stock     = findSheetRows(sheetsData, SHEETS_STOCK);

        if (nouvelles == null)
            throw new IllegalArgumentException("Feuille 'Nouvelle souscription' introuvable dans le fichier.");
        if (anciennes == null)
            throw new IllegalArgumentException("Feuille 'Ancienne souscription' introuvable dans le fichier.");
        if (stock == null)
            throw new IllegalArgumentException("Feuille 'Stock du mois' introuvable dans le fichier.");

        log.info("Parsing XLSB OK — Nouvelles: {}, Anciennes: {}, Stock: {}",
            nouvelles.size(), anciennes.size(), stock.size());
        return new ExcelData(nouvelles, anciennes, stock);
    }

    private List<Map<String, Object>> findSheetRows(Map<String, List<Map<String, Object>>> data, List<String> keywords) {
        // 1. Correspondance exacte
        for (String kw : keywords) {
            if (data.containsKey(kw)) return data.get(kw);
        }
        // 2. Correspondance partielle (le nom de la feuille contient le mot-clé)
        for (Map.Entry<String, List<Map<String, Object>>> entry : data.entrySet()) {
            String sheetName = entry.getKey();
            for (String kw : keywords) {
                if (sheetName.contains(kw)) return entry.getValue();
            }
        }
        return null;
    }

    private static class XlsbRowCollector implements XSSFBSheetHandler.SheetContentsHandler {
        private final boolean allowNomFallback;
        private final List<Map<String, Object>> rows = new ArrayList<>();
        private final List<String> headers = new ArrayList<>();
        // En-têtes tentatives pour la ligne courante (avant de confirmer que c'est la ligne d'en-tête)
        private final List<String> pendingHeaders = new ArrayList<>();
        private Map<String, Object> currentRow;
        private boolean headerFound = false;
        private boolean currentRowHasClientCol = false;
        private boolean currentRowHasNomCol = false;

        XlsbRowCollector(boolean allowNomFallback) { this.allowNomFallback = allowNomFallback; }
        XlsbRowCollector() { this(false); }

        @Override
        public void startRow(int rowNum) {
            currentRow = new LinkedHashMap<>();
            pendingHeaders.clear();
            currentRowHasClientCol = false;
            currentRowHasNomCol = false;
        }

        @Override
        public void endRow(int rowNum) {
            if (!headerFound) {
                if (currentRowHasClientCol || (allowNomFallback && currentRowHasNomCol)) {
                    // Normaliser COMPTE → CLIENT si CLIENT absent (certains fichiers utilisent COMPTE comme identifiant)
                    if (!pendingHeaders.contains("CLIENT") && pendingHeaders.contains("COMPTE")) {
                        pendingHeaders.set(pendingHeaders.indexOf("COMPTE"), "CLIENT");
                    }
                    headers.addAll(pendingHeaders);
                    headerFound = true;
                }
                // Sinon c'est une ligne de titre/métadonnée, on l'ignore
            } else if (currentRow != null) {
                Object clientVal = currentRow.get("CLIENT");
                Object nomVal    = currentRow.get("NOM");
                if ((clientVal != null && !clientVal.toString().isBlank()) ||
                    (allowNomFallback && nomVal != null && !nomVal.toString().isBlank())) {
                    rows.add(currentRow);
                }
            }
            currentRow = null;
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            if (cellReference == null) return;
            int col = new CellReference(cellReference).getCol();
            if (!headerFound) {
                // Construire les en-têtes provisoires pour cette ligne
                while (pendingHeaders.size() <= col) pendingHeaders.add("COL_" + pendingHeaders.size());
                String colName = formattedValue != null ? formattedValue.trim().toUpperCase() : "COL_" + col;
                pendingHeaders.set(col, colName);
                if ("CLIENT".equals(colName) || "COMPTE".equals(colName)) currentRowHasClientCol = true;
                if ("NOM".equals(colName)) currentRowHasNomCol = true;
            } else if (currentRow != null && col < headers.size()) {
                if (formattedValue != null && !formattedValue.isBlank()) {
                    currentRow.put(headers.get(col), formattedValue.trim());
                }
            }
        }

        @Override
        public void hyperlinkCell(String cellReference, String formattedValue, String url, String label, XSSFComment comment) {}

        public List<Map<String, Object>> getRows() { return rows; }
        public List<String> getHeaders() { return headers; }
    }

    /**
     * Parse une feuille et retourne une liste de Map<colonne, valeur>.
     * allowNomFallback : si true, accepte aussi une ligne d'en-tête sans CLIENT (mais avec NOM)
     *                    et conserve les lignes sans CLIENT mais avec NOM non vide.
     *                    Doit être true uniquement pour la feuille stock.
     */
    private List<Map<String, Object>> parseSheet(Sheet sheet, boolean allowNomFallback) {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> headers = new ArrayList<>();
        boolean headerFound = false;

        for (Row row : sheet) {
            if (!headerFound) {
                List<String> candidats = new ArrayList<>();
                for (Cell cell : row) {
                    String header = getCellStringValue(cell);
                    candidats.add(header != null ? header.trim().toUpperCase() : "COL_" + cell.getColumnIndex());
                }
                if (candidats.contains("CLIENT") || candidats.contains("COMPTE") || (allowNomFallback && candidats.contains("NOM"))) {
                    // Normaliser COMPTE → CLIENT si CLIENT absent
                    if (!candidats.contains("CLIENT") && candidats.contains("COMPTE")) {
                        candidats.set(candidats.indexOf("COMPTE"), "CLIENT");
                    }
                    headers.addAll(candidats);
                    headerFound = true;
                }
                continue;
            }
            if (isRowEmpty(row)) continue;

            Map<String, Object> rowData = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                rowData.put(headers.get(i), parseCellValue(cell));
            }

            Object clientVal = rowData.get("CLIENT");
            Object nomVal    = rowData.get("NOM");
            if ((clientVal != null && !clientVal.toString().isBlank()) ||
                (allowNomFallback && nomVal != null && !nomVal.toString().isBlank())) {
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
            .numeroClient(getNumeroClient(row))
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

    /**
     * Retourne le numéro client depuis la colonne CLIENT ou COMPTE.
     * Certains fichiers utilisent COMPTE (numéro de compte bancaire) comme identifiant.
     * Format compte : 0 + numeroClient + clé(4 chiffres) → on extrait la partie centrale.
     * Ex : "02117730005" → "211773"
     */
    public String getNumeroClient(Map<String, Object> row) {
        String num = getString(row, "CLIENT");
        if (num == null) num = getString(row, "COMPTE");
        if (num != null && num.length() > 7) {
            // Numéro de compte bancaire : enlever 1er chiffre + 4 derniers
            num = num.substring(1, num.length() - 4).trim();
        }
        return num == null || num.isBlank() ? null : num;
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

    private Sheet findSheet(Workbook wb, List<String> keywords) {
        // 1. Correspondance exacte
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            String sheetName = wb.getSheetName(i).toLowerCase().trim();
            if (keywords.contains(sheetName)) return wb.getSheetAt(i);
        }
        // 2. Correspondance partielle (le nom contient un mot-clé)
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            String sheetName = wb.getSheetName(i).toLowerCase().trim();
            for (String kw : keywords) {
                if (sheetName.contains(kw)) return wb.getSheetAt(i);
            }
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
