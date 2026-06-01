package com.securicompte.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * Parser dédié aux fichiers Excel SIB.
 * La SIB fournit 2 fichiers par mois (feuille "Exportation") :
 *   - Nouvelles souscriptions  → nouvelles
 *   - Renouvellement           → anciennes (stock existant)
 * Les colonnes SIB sont normalisées vers les clés BNI internes pour
 * être compatibles avec ExcelParserService.rowToClient/rowToSouscription/rowToStock.
 */
@Service
@Slf4j
public class SibParserService {

    // Feuille de données SIB
    private static final String SHEET_EXPORTATION = "exportation";

    /**
     * Parse les deux fichiers SIB et retourne un ExcelData unifié.
     * @param fichierNouvelles  fichier "nouvelles souscriptions"
     * @param fichierRenouvellement fichier "renouvellement" (anciennes)
     */
    public ExcelParserService.ExcelData parse(MultipartFile fichierNouvelles,
                                               MultipartFile fichierRenouvellement) throws IOException {
        List<Map<String, Object>> rowsNouvelles = parseExportation(fichierNouvelles, "nouvelles");
        List<Map<String, Object>> rowsAncien    = parseExportation(fichierRenouvellement, "renouvellement");

        Set<String> numerosNouvelles = new HashSet<>();
        for (Map<String, Object> r : rowsNouvelles) {
            String num = getNum(r);
            if (num != null) numerosNouvelles.add(num);
        }

        Set<String> numerosAnciennes = new HashSet<>();
        for (Map<String, Object> r : rowsAncien) {
            String num = getNum(r);
            if (num != null) numerosAnciennes.add(num);
        }

        // Stock = nouvelles + anciennes
        List<Map<String, Object>> stock = new ArrayList<>(rowsNouvelles);
        stock.addAll(rowsAncien);

        log.info("SIB parse OK — Stock: {}, Nouvelles: {}, Anciennes: {}",
            stock.size(), numerosNouvelles.size(), numerosAnciennes.size());

        return new ExcelParserService.ExcelData(stock, numerosNouvelles, numerosAnciennes);
    }

    private String getNum(Map<String, Object> row) {
        Object v = row.get("CLIENT");
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private List<Map<String, Object>> parseExportation(MultipartFile file, String label) throws IOException {
        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = findExportationSheet(wb);
            if (sheet == null) {
                List<String> noms = new ArrayList<>();
                for (int i = 0; i < wb.getNumberOfSheets(); i++) noms.add(wb.getSheetName(i));
                throw new IllegalArgumentException(
                    "Feuille 'Exportation' introuvable dans le fichier SIB (" + label + "). Feuilles présentes : " + noms);
            }
            List<Map<String, Object>> rows = parseSibSheet(sheet);
            log.info("SIB feuille '{}' ({}) — {} lignes", sheet.getSheetName(), label, rows.size());
            return rows;
        }
    }

    private Sheet findExportationSheet(Workbook wb) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            if (wb.getSheetName(i).toLowerCase().trim().contains(SHEET_EXPORTATION)) {
                return wb.getSheetAt(i);
            }
        }
        return null;
    }

    private List<Map<String, Object>> parseSibSheet(Sheet sheet) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<String> sibHeaders = new ArrayList<>();
        boolean headerFound = false;
        DataFormatter fmt = new DataFormatter();

        for (Row row : sheet) {
            if (!headerFound) {
                List<String> candidats = new ArrayList<>();
                for (Cell cell : row) {
                    String v = getCellString(cell, fmt);
                    candidats.add(v != null ? v.trim() : "COL_" + cell.getColumnIndex());
                }
                // La ligne header SIB contient "Numéro Client"
                if (candidats.stream().anyMatch(h -> h.toLowerCase().contains("num") && h.toLowerCase().contains("client"))) {
                    sibHeaders.addAll(candidats);
                    headerFound = true;
                }
                continue;
            }

            if (isRowEmpty(row)) continue;

            // Lire la ligne brute SIB
            Map<String, String> raw = new LinkedHashMap<>();
            for (int i = 0; i < sibHeaders.size(); i++) {
                Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                String val = getCellString(cell, fmt);
                if (val != null && !val.isBlank()) raw.put(sibHeaders.get(i), val.trim());
            }

            // Ignorer les lignes sans numéro client
            String numClient = findSibNumeroClient(raw);
            if (numClient == null) continue;

            // Normaliser vers les clés internes BNI
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("CLIENT", numClient);
            normalized.put("NOM", buildNom(raw));
            normalized.put("AGENCELIB", findVal(raw, "agence"));
            normalized.put("DATNAISSANCE", findVal(raw, "naissance"));
            normalized.put("DATSOUSCRIPTION", findVal(raw, "effet"));
            normalized.put("SECURICOMPTE", findVal(raw, "prime"));
            normalized.put("OPTION SECURICOMPTE", findVal(raw, "option securicompte"));
            normalized.put("LIBELL_PACKAGE", findVal(raw, "type de package"));
            normalized.put("COMPTE", findVal(raw, "contrat"));

            result.add(normalized);
        }
        return result;
    }

    private String findSibNumeroClient(Map<String, String> raw) {
        for (Map.Entry<String, String> e : raw.entrySet()) {
            String k = e.getKey().toLowerCase();
            if (k.contains("num") && k.contains("client")) return e.getValue();
        }
        return null;
    }

    private String buildNom(Map<String, String> raw) {
        String nom    = null;
        String prenom = null;
        for (Map.Entry<String, String> e : raw.entrySet()) {
            String k = e.getKey().toLowerCase();
            if (k.equals("nom client"))    nom    = e.getValue();
            if (k.equals("prenom client")) prenom = e.getValue();
        }
        if (nom == null && prenom == null) return null;
        if (nom == null)    return prenom;
        if (prenom == null) return nom;
        return nom + " " + prenom;
    }

    private String findVal(Map<String, String> raw, String keyword) {
        for (Map.Entry<String, String> e : raw.entrySet()) {
            if (e.getKey().toLowerCase().contains(keyword)) return e.getValue();
        }
        return null;
    }

    private String getCellString(Cell cell, DataFormatter fmt) {
        if (cell == null) return null;
        String s = fmt.formatCellValue(cell).trim();
        return s.isEmpty() ? null : s;
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
