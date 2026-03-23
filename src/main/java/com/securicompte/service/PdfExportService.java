package com.securicompte.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.securicompte.dto.ImpayeDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
