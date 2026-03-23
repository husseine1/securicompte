package com.securicompte.service;

import com.securicompte.dto.ImpayeDto;
import com.securicompte.enums.StatutImpaye;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PdfExportService - export PDF impayés")
class PdfExportServiceTest {

    private final PdfExportService pdfExportService = new PdfExportService();

    // ── helpers ──────────────────────────────────────────────────────────────

    private ImpayeDto buildImpayeDto(String nom, StatutImpaye statut) {
        return ImpayeDto.builder()
                .nomClient(nom)
                .numeroClient("C001")
                .agenceLib("Agence Centre")
                .moisNom("Janvier")
                .annee(2024)
                .statut(statut)
                .securicompte("SC-BASE")
                .montantDu(new BigDecimal("1500.00"))
                .dateDetection(LocalDateTime.of(2024, 2, 1, 0, 0))
                .dateRegularisation(statut == StatutImpaye.REGULARISE ? LocalDateTime.of(2024, 3, 15, 0, 0) : null)
                .build();
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Export avec liste vide → PDF valide généré (non vide, commence par %PDF)")
    void exporterImpayes_listeVide_pdfValide() throws IOException {
        byte[] pdf = pdfExportService.exporterImpayes(List.of());

        assertThat(pdf).isNotEmpty();
        // Signature PDF : %PDF
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("Export avec plusieurs impayés → PDF non vide contenant les données")
    void exporterImpayes_avecDonnees_pdfNonVide() throws IOException {
        List<ImpayeDto> impayes = List.of(
                buildImpayeDto("Dupont Jean", StatutImpaye.IMPAYE),
                buildImpayeDto("Martin Sophie", StatutImpaye.REGULARISE),
                buildImpayeDto("Bernard Paul", StatutImpaye.LITIGE)
        );

        byte[] pdf = pdfExportService.exporterImpayes(impayes);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        // PDF avec données doit être plus grand qu'un PDF vide
        byte[] pdfVide = pdfExportService.exporterImpayes(List.of());
        assertThat(pdf.length).isGreaterThan(pdfVide.length);
    }

    @Test
    @DisplayName("Export avec impayé dont les champs optionnels sont null → pas d'exception")
    void exporterImpayes_champsNull_pasException() throws IOException {
        ImpayeDto dto = ImpayeDto.builder()
                .nomClient(null)
                .numeroClient(null)
                .statut(null)
                .montantDu(null)
                .dateDetection(null)
                .dateRegularisation(null)
                .build();

        byte[] pdf = pdfExportService.exporterImpayes(List.of(dto));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("Export grand volume → PDF généré sans erreur")
    void exporterImpayes_grandVolume_pdfGenere() throws IOException {
        List<ImpayeDto> impayes = java.util.stream.IntStream.range(0, 200)
                .mapToObj(i -> buildImpayeDto("Client " + i,
                        i % 2 == 0 ? StatutImpaye.IMPAYE : StatutImpaye.REGULARISE))
                .toList();

        byte[] pdf = pdfExportService.exporterImpayes(impayes);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }
}
