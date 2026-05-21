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

    private static final List<String> SHEETS_STOCK =
        List.of("stock du mois", "stock mois", "stock mensuel", "stock");

    private static final List<String> SHEETS_NOUVELLES =
        List.of("nouvelles souscriptions", "nouvelle souscription", "nouvelles");

    public record ExcelData(List<Map<String, Object>> stock, Set<String> numerosNouvelles) {}

    /**
     * Parse le fichier Excel — seule la feuille "Stock du mois" est utilisée.
     * Le type NOUVELLE/ANCIENNE est déduit de la date de souscription dans ImportService.
     */
    public ExcelData parseExcel(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase().endsWith(".xlsb")) {
            return parseXlsb(file);
        }
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheetStock = findSheet(workbook, SHEETS_STOCK);

            List<String> sheetsFound = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) sheetsFound.add(workbook.getSheetName(i));

            if (sheetStock == null)
                throw new IllegalArgumentException("Feuille 'Stock du mois' introuvable. Feuilles présentes : " + sheetsFound);

            List<Map<String, Object>> stock = parseSheet(sheetStock, true);

            Set<String> numerosNouvelles = new HashSet<>();
            Sheet sheetNouvelles = findSheet(workbook, SHEETS_NOUVELLES);
            if (sheetNouvelles != null) {
                for (Map<String, Object> row : parseSheet(sheetNouvelles, false)) {
                    String num = getNumeroClient(row);
                    if (num != null) numerosNouvelles.add(num);
                }
                log.info("Feuille nouvelles souscriptions — {} client(s) nouveau(x)", numerosNouvelles.size());
            } else {
                log.warn("Feuille 'nouvelles souscriptions' introuvable — classification par date de souscription");
            }

            log.info("Parsing Excel OK — Stock: {}", stock.size());
            return new ExcelData(stock, numerosNouvelles);
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

        List<Map<String, Object>> stock = findSheetRows(sheetsData, SHEETS_STOCK);

        if (stock == null)
            throw new IllegalArgumentException("Feuille 'Stock du mois' introuvable dans le fichier.");

        Set<String> numerosNouvelles = new HashSet<>();
        List<Map<String, Object>> nouvellesRows = findSheetRows(sheetsData, SHEETS_NOUVELLES);
        if (nouvellesRows != null) {
            for (Map<String, Object> row : nouvellesRows) {
                String num = getNumeroClient(row);
                if (num != null) numerosNouvelles.add(num);
            }
            log.info("Feuille nouvelles souscriptions — {} client(s) nouveau(x)", numerosNouvelles.size());
        } else {
            log.warn("Feuille 'nouvelles souscriptions' introuvable — classification par date de souscription");
        }

        log.info("Parsing XLSB OK — Stock: {}", stock.size());
        return new ExcelData(stock, numerosNouvelles);
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
        private final List<String> pendingHeaders = new ArrayList<>();
        private Map<String, Object> currentRow;
        private boolean headerFound = false;
        private boolean done = false;
        private boolean currentRowHasClientCol = false;
        private boolean currentRowHasNomCol = false;

        XlsbRowCollector(boolean allowNomFallback) { this.allowNomFallback = allowNomFallback; }
        XlsbRowCollector() { this(false); }

        @Override
        public void startRow(int rowNum) {
            if (done) return;
            currentRow = new LinkedHashMap<>();
            pendingHeaders.clear();
            currentRowHasClientCol = false;
            currentRowHasNomCol = false;
        }

        @Override
        public void endRow(int rowNum) {
            if (done) { currentRow = null; return; }
            if (!headerFound) {
                if (currentRowHasClientCol || (allowNomFallback && currentRowHasNomCol)) {
                    if (!pendingHeaders.contains("CLIENT") && pendingHeaders.contains("COMPTE")) {
                        pendingHeaders.set(pendingHeaders.indexOf("COMPTE"), "CLIENT");
                    }
                    headers.addAll(pendingHeaders);
                    headerFound = true;
                }
            } else if (currentRow != null) {
                Object clientVal = currentRow.get("CLIENT");
                Object nomVal    = currentRow.get("NOM");
                if (clientVal != null && !clientVal.toString().isBlank()) {
                    rows.add(currentRow);
                } else if (allowNomFallback && nomVal != null && !nomVal.toString().isBlank()) {
                    // NOM présent mais pas de CLIENT → ligne de total → fin du stock
                    done = true;
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
            if (clientVal != null && !clientVal.toString().isBlank()) {
                rows.add(rowData);
            } else if (allowNomFallback && nomVal != null && !nomVal.toString().isBlank()) {
                // NOM présent mais pas de CLIENT → ligne de total → fin du stock
                break;
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
            .securicompte(getSecuricompte(row))
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
            .securicompte(getSecuricompte(row))
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

    public LocalDate getDate(Map<String, Object> row, String key) {
        Object val = row.get(key.toUpperCase());
        if (val instanceof LocalDate ld) return ld;
        // Numéro de série Excel stocké comme Number (ex: cellule non formatée date)
        if (val instanceof Number n) {
            double d = n.doubleValue();
            if (d > 1000) {
                try { return DateUtil.getLocalDateTime(d).toLocalDate(); } catch (Exception ignored) {}
            }
        }
        if (val instanceof String s && !s.isBlank()) {
            s = s.trim();
            // dd/MM/yyyy ou dd/MM/yy
            String[] parts = s.split("/");
            if (parts.length == 3) {
                try {
                    int year = Integer.parseInt(parts[2]);
                    if (year < 100) year += 2000;
                    return LocalDate.of(year, Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
                } catch (Exception ignored) {}
            }
            // ISO yyyy-MM-dd
            try { return LocalDate.parse(s); } catch (Exception ignored) {}
            // Numéro de série Excel comme chaîne (ex: "44806")
            try {
                double serial = Double.parseDouble(s);
                if (serial > 1000) return DateUtil.getLocalDateTime(serial).toLocalDate();
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Normalise la valeur de la colonne SECURICOMPTE :
     * - Supprime les espaces superflus et met en majuscules
     * - Corrige l'artefact Excel des entiers flottants : "365.0" → "365"
     * - Normalise les variantes booléennes (OUI/O/1/YES → "OUI", NON/N/0/NO → "NON")
     */
    public String getSecuricompte(Map<String, Object> row) {
        Object val = row.get("SECURICOMPTE");
        if (val == null) return null;

        // Excel stocke parfois les entiers comme doubles : 365.0, 730.0…
        if (val instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }

        String s = val.toString().trim().toUpperCase();
        if (s.isEmpty()) return null;

        // Artefact float texte : "365.0" → "365"
        if (s.matches("^\\d+\\.0+$")) {
            s = s.substring(0, s.indexOf('.'));
        }

        // Normalisation booléenne
        return switch (s) {
            case "OUI", "O", "1", "YES", "TRUE", "VRAI" -> "OUI";
            case "NON", "N", "0", "NO", "FALSE", "FAUX" -> "NON";
            default -> s;
        };
    }

    /**
     * Parse une valeur numérique en gérant tous les formats monétaires courants :
     * - Séparateurs de milliers : espace, espace insécable (NBSP), point
     * - Séparateur décimal : virgule ou point
     * - Suffixes monétaires : FCFA, F, XOF, CFA, etc.
     * Exemples : "1 234,56" → 1234.56 | "1.234,56" → 1234.56 | "54,75" → 54.75
     */
    private BigDecimal getDecimal(Map<String, Object> row, String key) {
        Object val = row.get(key.toUpperCase());
        if (val == null) return null;
        try {
            if (val instanceof Number n) {
                double d = n.doubleValue();
                return (d == 0) ? BigDecimal.ZERO : BigDecimal.valueOf(d);
            }
            String s = val.toString().trim();
            if (s.isEmpty() || s.equals("-") || s.equalsIgnoreCase("N/A")) return null;

            // Supprimer les suffixes monétaires (lettres en fin de chaîne)
            s = s.replaceAll("(?i)[A-Z]+\\s*$", "").trim();
            // Supprimer espaces et espaces insécables (séparateurs de milliers)
            s = s.replace(" ", "").replace(" ", "");

            if (s.isEmpty()) return null;

            // Virgule ET point : format européen "1.234,56" → point = milliers, virgule = décimal
            if (s.contains(",") && s.contains(".")) {
                int lastComma = s.lastIndexOf(',');
                int lastDot   = s.lastIndexOf('.');
                if (lastComma > lastDot) {
                    // "1.234,56" : virgule est le décimal
                    s = s.replace(".", "").replace(",", ".");
                } else {
                    // "1,234.56" : point est le décimal
                    s = s.replace(",", "");
                }
            } else {
                // Seulement virgule : format français "1234,56"
                s = s.replace(",", ".");
            }

            return s.isEmpty() ? null : new BigDecimal(s);
        } catch (Exception e) {
            log.debug("Impossible de parser la valeur décimale '{}' pour la clé '{}'", val, key);
            return null;
        }
    }

    private final DataFormatter dataFormatter = new DataFormatter();

    private Object parseCellValue(Cell cell) {
        if (cell == null) return null;
        CellType effectiveType = cell.getCellType() == CellType.FORMULA
            ? cell.getCachedFormulaResultType()
            : cell.getCellType();
        // Cellule numérique formatée date → LocalDate directement
        if (effectiveType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String formatted = dataFormatter.formatCellValue(cell).trim();
        if (formatted.isEmpty()) return null;
        // Texte qui ressemble à une date → on normalise en LocalDate
        LocalDate parsedDate = tryParseDate(formatted);
        return parsedDate != null ? parsedDate : formatted;
    }

    /** Tente de parser une chaîne en LocalDate selon les formats courants des fichiers Excel. */
    private LocalDate tryParseDate(String s) {
        // dd/MM/yyyy ou dd/MM/yy
        String[] parts = s.split("[/\\-\\.]");
        if (parts.length == 3) {
            try {
                int p0 = Integer.parseInt(parts[0].trim());
                int p1 = Integer.parseInt(parts[1].trim());
                int p2 = Integer.parseInt(parts[2].trim());
                // jj/mm/aaaa ou jj/mm/aa (jour ≤ 31, mois ≤ 12)
                if (p0 >= 1 && p0 <= 31 && p1 >= 1 && p1 <= 12) {
                    int year = p2 < 100 ? p2 + 2000 : p2;
                    return LocalDate.of(year, p1, p0);
                }
                // aaaa/mm/jj (ISO avec séparateurs variés)
                if (p0 > 31 && p1 >= 1 && p1 <= 12 && p2 >= 1 && p2 <= 31) {
                    return LocalDate.of(p0, p1, p2);
                }
            } catch (Exception ignored) {}
        }
        // Numéro de série Excel en texte (ex: "45474")
        try {
            double serial = Double.parseDouble(s);
            if (serial > 1000 && serial < 100000) {
                return DateUtil.getLocalDateTime(serial).toLocalDate();
            }
        } catch (Exception ignored) {}
        return null;
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
