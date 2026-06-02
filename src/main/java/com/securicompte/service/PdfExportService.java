package com.securicompte.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.securicompte.dto.ImpayeDto;
import com.securicompte.dto.StatAgenceDto;
import com.securicompte.entity.ImportFichier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class PdfExportService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color SC_PRIMARY = new Color(26, 58, 92);
    private static final Color SC_ACCENT  = new Color(232, 184, 0);
    private static final Color ROW_ALT    = new Color(240, 244, 248);

    public byte[] exporterImpayes(List<ImpayeDto> impayes) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 20, 20, 30, 30);
            PdfWriter.getInstance(doc, out);
            doc.open();

            // ── En-tête ───────────────────────────────────────────────────────
            Font titleFont = new Font(Font.HELVETICA, 15, Font.BOLD, Color.WHITE);
            PdfPTable header = new PdfPTable(1);
            header.setWidthPercentage(100);
            PdfPCell titleCell = new PdfPCell(new Phrase("Rapport des Impayés — Gestion des Impayés SecuriCompte", titleFont));
            titleCell.setBackgroundColor(SC_PRIMARY);
            titleCell.setPadding(10);
            titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            titleCell.setBorder(Rectangle.NO_BORDER);
            header.addCell(titleCell);
            doc.add(header);
            doc.add(Chunk.NEWLINE);

            // ── Sous-titre ────────────────────────────────────────────────────
            Font subFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.DARK_GRAY);
            doc.add(new Paragraph("Total : " + impayes.size() + " enregistrement(s)", subFont));
            doc.add(Chunk.NEWLINE);

            // ── Tableau ───────────────────────────────────────────────────────
            float[] widths = {3f, 2f, 2.5f, 2f, 2f, 2f, 2f, 2f, 2f};
            PdfPTable table = new PdfPTable(widths);
            table.setWidthPercentage(100);

            String[] cols = {"Client", "N° Client", "Agence", "Période", "Statut",
                    "SecuriCompte", "Commission", "Détecté le", "Régularisé le"};
            Font hFont = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
            for (String col : cols) {
                PdfPCell c = new PdfPCell(new Phrase(col, hFont));
                c.setBackgroundColor(SC_PRIMARY);
                c.setPadding(5);
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(c);
            }

            // Lignes
            Font cFont    = new Font(Font.HELVETICA, 8);
            Font redFont  = new Font(Font.HELVETICA, 8, Font.BOLD, new Color(185, 28, 28));
            Font greenFont= new Font(Font.HELVETICA, 8, Font.BOLD, new Color(21, 128, 61));
            boolean alt = false;

            for (ImpayeDto i : impayes) {
                Color bg = alt ? ROW_ALT : Color.WHITE;
                alt = !alt;

                addCell(table, nvl(i.getNomClient()),    cFont, bg);
                addCell(table, nvl(i.getNumeroClient()), cFont, bg);
                addCell(table, nvl(i.getAgenceLib()),    cFont, bg);
                String periode = (i.getMoisNom() != null ? i.getMoisNom() : "") + " " + (i.getAnnee() != null ? i.getAnnee() : "");
                addCell(table, periode.trim(), cFont, bg);

                String statut = i.getStatut() != null ? i.getStatut().name() : "-";
                Font sf = "IMPAYE".equals(statut) ? redFont : ("REGULARISE".equals(statut) ? greenFont : cFont);
                addCell(table, statut, sf, bg);

                addCell(table, nvl(i.getSecuricompte()),  cFont, bg);
                addCell(table, i.getMontantDu() != null ? i.getMontantDu().toPlainString() : "-", cFont, bg);
                addCell(table, i.getDateDetection()      != null ? i.getDateDetection().format(FMT) : "-", cFont, bg);
                addCell(table, i.getDateRegularisation() != null ? i.getDateRegularisation().format(FMT) : "-", cFont, bg);
            }

            doc.add(table);

            // ── Pied de page ──────────────────────────────────────────────────
            doc.add(Chunk.NEWLINE);
            Font footFont = new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY);
            doc.add(new Paragraph("Généré par Gestion des Impayés SecuriCompte", footFont));

            doc.close();
            return out.toByteArray();
        }
    }

    private static final String[] MOIS_NOMS = {
        "", "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
        "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"
    };

    public byte[] genererRapportMensuel(ImportFichier imp, List<ImpayeDto> impayes,
                                         List<StatAgenceDto> statsAgence) throws IOException {
        String reseau = imp.getReseau() != null ? imp.getReseau().name() : "BNI";
        String periode = MOIS_NOMS[imp.getMois()] + " " + imp.getAnnee();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 30, 30, 40, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();

            // ── En-tête ─────────────────────────────────────────────────────
            Font titleFont  = new Font(Font.HELVETICA, 16, Font.BOLD, Color.WHITE);
            Font subTFont   = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.WHITE);
            PdfPTable header = new PdfPTable(1);
            header.setWidthPercentage(100);
            PdfPCell titleCell = new PdfPCell();
            titleCell.setBackgroundColor(SC_PRIMARY);
            titleCell.setPadding(12);
            titleCell.setBorder(Rectangle.NO_BORDER);
            titleCell.addElement(new Phrase("Rapport Mensuel — " + periode + " | Réseau " + reseau, titleFont));
            titleCell.addElement(new Phrase("Généré le " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), subTFont));
            header.addCell(titleCell);
            doc.add(header);
            doc.add(Chunk.NEWLINE);

            // ── Section 1 : Récapitulatif import ────────────────────────────
            Font secFont  = new Font(Font.HELVETICA, 11, Font.BOLD, SC_PRIMARY);
            Font lblFont  = new Font(Font.HELVETICA, 9, Font.BOLD, Color.DARK_GRAY);
            Font valFont  = new Font(Font.HELVETICA, 9);

            doc.add(new Paragraph("Récapitulatif de l'import", secFont));
            doc.add(Chunk.NEWLINE);

            PdfPTable recap = new PdfPTable(new float[]{3f, 2f, 3f, 2f});
            recap.setWidthPercentage(80);
            recap.setHorizontalAlignment(Element.ALIGN_LEFT);

            addRecapRow(recap, "Fichier importé", imp.getNomFichier(), lblFont, valFont);
            addRecapRow(recap, "Stock total", String.valueOf(imp.getNbStock()), lblFont, valFont);
            addRecapRow(recap, "Nouvelles souscriptions", String.valueOf(imp.getNbNouvelles()), lblFont, valFont);
            addRecapRow(recap, "Anciennes souscriptions", String.valueOf(imp.getNbAnciennes()), lblFont, valFont);
            addRecapRow(recap, "Impayés détectés", String.valueOf(impayes.size()), lblFont, valFont);
            addRecapRow(recap, "Erreurs lignes", String.valueOf(imp.getNbErreurs()), lblFont, valFont);
            doc.add(recap);
            doc.add(Chunk.NEWLINE);

            // ── Section 2 : Impayés du mois ─────────────────────────────────
            doc.add(new Paragraph("Impayés détectés ce mois (" + impayes.size() + ")", secFont));
            doc.add(Chunk.NEWLINE);

            if (impayes.isEmpty()) {
                Font okFont = new Font(Font.HELVETICA, 9, Font.ITALIC, new Color(21, 128, 61));
                doc.add(new Paragraph("Aucun impayé détecté pour ce mois.", okFont));
            } else {
                PdfPTable tbl = new PdfPTable(new float[]{2.5f, 3f, 3f, 2f, 2f});
                tbl.setWidthPercentage(100);
                Font hFont = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
                for (String col : new String[]{"N° Client", "Nom", "Agence", "Statut", "Détecté le"}) {
                    PdfPCell c = new PdfPCell(new Phrase(col, hFont));
                    c.setBackgroundColor(SC_PRIMARY); c.setPadding(5);
                    c.setHorizontalAlignment(Element.ALIGN_CENTER);
                    tbl.addCell(c);
                }
                Font cFont   = new Font(Font.HELVETICA, 8);
                Font redFont = new Font(Font.HELVETICA, 8, Font.BOLD, new Color(185, 28, 28));
                boolean alt  = false;
                for (ImpayeDto i : impayes) {
                    Color bg = alt ? ROW_ALT : Color.WHITE; alt = !alt;
                    addCell(tbl, nvl(i.getNumeroClient()), cFont, bg);
                    addCell(tbl, nvl(i.getNomClient()),    cFont, bg);
                    addCell(tbl, nvl(i.getAgenceLib()),    cFont, bg);
                    String st = i.getStatut() != null ? i.getStatut().name() : "-";
                    addCell(tbl, st, "IMPAYE".equals(st) ? redFont : cFont, bg);
                    addCell(tbl, i.getDateDetection() != null ? i.getDateDetection().format(FMT) : "-", cFont, bg);
                }
                doc.add(tbl);
            }
            doc.add(Chunk.NEWLINE);

            // ── Section 3 : Top agences ──────────────────────────────────────
            if (!statsAgence.isEmpty()) {
                doc.add(new Paragraph("Impayés par agence", secFont));
                doc.add(Chunk.NEWLINE);
                PdfPTable agTbl = new PdfPTable(new float[]{5f, 2f});
                agTbl.setWidthPercentage(60);
                agTbl.setHorizontalAlignment(Element.ALIGN_LEFT);
                Font hFont = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
                for (String col : new String[]{"Agence", "Nb impayés"}) {
                    PdfPCell c = new PdfPCell(new Phrase(col, hFont));
                    c.setBackgroundColor(SC_PRIMARY); c.setPadding(5);
                    agTbl.addCell(c);
                }
                Font cFont = new Font(Font.HELVETICA, 8);
                boolean alt = false;
                for (StatAgenceDto s : statsAgence) {
                    Color bg = alt ? ROW_ALT : Color.WHITE; alt = !alt;
                    addCell(agTbl, s.getAgence() != null ? s.getAgence() : "N/A", cFont, bg);
                    addCell(agTbl, String.valueOf(s.getNbImpayes()), cFont, bg);
                }
                doc.add(agTbl);
            }

            // ── Pied de page ─────────────────────────────────────────────────
            doc.add(Chunk.NEWLINE);
            Font footFont = new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY);
            doc.add(new Paragraph("Document généré par SecuriCompte — " +
                LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), footFont));

            doc.close();
            return out.toByteArray();
        }
    }

    private void addRecapRow(PdfPTable table, String label, String value, Font lblFont, Font valFont) {
        PdfPCell lbl = new PdfPCell(new Phrase(label, lblFont));
        lbl.setBorder(Rectangle.BOTTOM); lbl.setPadding(5);
        PdfPCell val = new PdfPCell(new Phrase(value != null ? value : "-", valFont));
        val.setBorder(Rectangle.BOTTOM); val.setPadding(5);
        table.addCell(lbl); table.addCell(val);
    }

    private void addCell(PdfPTable table, String text, Font font, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBackgroundColor(bg);
        c.setPadding(4);
        table.addCell(c);
    }

    private String nvl(Object o) {
        return o != null ? o.toString() : "-";
    }
}
