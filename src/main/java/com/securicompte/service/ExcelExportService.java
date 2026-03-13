package com.securicompte.service;

import com.securicompte.dto.ImpayeDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@Service
@Slf4j
public class ExcelExportService {

    /**
     * Exporte la liste des impayés vers un fichier Excel
     */
    public byte[] exporterImpaYes(List<ImpayeDto> impayes) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Impayés");

            // Styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle impayeStyle = createImpayeStyle(workbook);
            CellStyle regulariseStyle = createRegulariseStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);

            // Entêtes
            String[] headers = {
                "N°", "Numéro Client", "Nom Client", "Agence", "Gestionnaire",
                "Zone", "Année", "Mois", "Statut", "Montant Dû",
                "Date Détection", "Date Régularisation"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Données
            int rowNum = 1;
            for (ImpayeDto impaye : impayes) {
                Row row = sheet.createRow(rowNum++);
                CellStyle rowStyle = "IMPAYE".equals(impaye.getStatut() != null ?
                    impaye.getStatut().name() : "") ? impayeStyle : regulariseStyle;

                createCell(row, 0, rowNum - 1, null);
                createCell(row, 1, impaye.getNumeroClient(), rowStyle);
                createCell(row, 2, impaye.getNomClient(), rowStyle);
                createCell(row, 3, impaye.getAgenceLib(), rowStyle);
                createCell(row, 4, impaye.getGestionnaire(), rowStyle);
                createCell(row, 5, impaye.getZoneLib(), rowStyle);
                createCell(row, 6, impaye.getAnnee(), rowStyle);
                createCell(row, 7, impaye.getMoisNom(), rowStyle);
                createCell(row, 8, impaye.getStatut() != null ? impaye.getStatut().getLibelle() : "", rowStyle);
                createCell(row, 9, impaye.getMontantDu(), rowStyle);
                createCell(row, 10,
                    impaye.getDateDetection() != null ? impaye.getDateDetection().toLocalDate().toString() : "",
                    rowStyle);
                createCell(row, 11,
                    impaye.getDateRegularisation() != null ? impaye.getDateRegularisation().toLocalDate().toString() : "",
                    rowStyle);
            }

            // Ajuster les colonnes
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Résumé
            addSummarySheet(workbook, impayes);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void addSummarySheet(Workbook workbook, List<ImpayeDto> impayes) {
        Sheet sheet = workbook.createSheet("Résumé");
        CellStyle headerStyle = createHeaderStyle(workbook);

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("RAPPORT IMPAYÉS - SECURICOMPTE");

        sheet.createRow(2).createCell(0).setCellValue("Total impayés:");
        sheet.getRow(2).createCell(1).setCellValue(impayes.size());

        long nbImpayes = impayes.stream()
            .filter(i -> i.getStatut() != null && "IMPAYE".equals(i.getStatut().name()))
            .count();
        long nbRegularises = impayes.size() - nbImpayes;

        sheet.createRow(3).createCell(0).setCellValue("Non régularisés:");
        sheet.getRow(3).createCell(1).setCellValue(nbImpayes);

        sheet.createRow(4).createCell(0).setCellValue("Régularisés:");
        sheet.getRow(4).createCell(1).setCellValue(nbRegularises);

        BigDecimal montantTotal = impayes.stream()
            .filter(i -> i.getMontantDu() != null && i.getStatut() != null
                && "IMPAYE".equals(i.getStatut().name()))
            .map(ImpayeDto::getMontantDu)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        sheet.createRow(5).createCell(0).setCellValue("Montant total dû:");
        sheet.getRow(5).createCell(1).setCellValue(montantTotal.doubleValue());

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void createCell(Row row, int col, Object value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Integer i) {
            cell.setCellValue(i);
        } else if (value instanceof Long l) {
            cell.setCellValue(l);
        } else if (value instanceof BigDecimal bd) {
            cell.setCellValue(bd.doubleValue());
        } else if (value instanceof Double d) {
            cell.setCellValue(d);
        } else {
            cell.setCellValue(value.toString());
        }
        if (style != null) cell.setCellStyle(style);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createImpayeStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle createRegulariseStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        CreationHelper helper = workbook.getCreationHelper();
        style.setDataFormat(helper.createDataFormat().getFormat("dd/mm/yyyy"));
        return style;
    }
}
