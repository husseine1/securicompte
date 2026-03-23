package com.securicompte.controller;

import com.securicompte.dto.FiltreImpayeDto;
import com.securicompte.dto.ImpayeDto;
import com.securicompte.enums.StatutImpaye;
import com.securicompte.service.ClientService;
import com.securicompte.service.ExcelExportService;
import com.securicompte.service.ImpayeService;
import com.securicompte.service.PdfExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.securicompte.entity.User;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequestMapping("/impayes")
@RequiredArgsConstructor
@Slf4j
public class ImpayeController {

    private final ImpayeService      impayeService;
    private final ClientService      clientService;
    private final ExcelExportService excelExportService;
    private final PdfExportService   pdfExportService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String listeImpayes(
            @RequestParam(required = false) Integer annee,
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) String agence,
            @RequestParam(required = false) String gestionnaire,
            @RequestParam(required = false) String statut,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        FiltreImpayeDto filtre = FiltreImpayeDto.builder()
            .annee(annee)
            .mois(mois)
            .agence(agence != null && !agence.isBlank() ? agence : null)
            .gestionnaire(gestionnaire != null && !gestionnaire.isBlank() ? gestionnaire : null)
            .statut(statut != null && !statut.isEmpty() ? StatutImpaye.valueOf(statut) : null)
            .page(page)
            .size(25)
            .build();

        Page<ImpayeDto> impayes = impayeService.getImpaYesWithFilters(filtre);

        model.addAttribute("impayes", impayes);
        model.addAttribute("filtre", filtre);
        model.addAttribute("agences", clientService.getAgences());
        model.addAttribute("gestionnaires", clientService.getGestionnaires());
        model.addAttribute("annees", impayeService.getAnnees());
        model.addAttribute("moisList", getMoisList());
        model.addAttribute("totalPages", impayes.getTotalPages());
        model.addAttribute("totalElements", impayes.getTotalElements());

        return "impaye/liste";
    }

    @PostMapping("/{id}/regulariser")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    @ResponseBody
    public String regulariser(@PathVariable Long id,
                               @RequestParam(required = false) String commentaire,
                               @AuthenticationPrincipal User currentUser) {
        boolean ok = impayeService.regulariser(id, commentaire, currentUser);
        return ok ? "OK" : "ERREUR";
    }

    @PostMapping("/{id}/marquer-impaye")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    @ResponseBody
    public String marquerImpaye(@PathVariable Long id,
                                @AuthenticationPrincipal User currentUser) {
        boolean ok = impayeService.marquerImpaye(id, currentUser);
        return ok ? "OK" : "ERREUR";
    }

    @GetMapping("/export")
    @PreAuthorize("isAuthenticated()")
    public void exporterExcel(
            @RequestParam(required = false) Integer annee,
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) String agence,
            @RequestParam(required = false) String gestionnaire,
            @RequestParam(required = false) String statut,
            HttpServletResponse response) throws IOException {

        FiltreImpayeDto filtre = FiltreImpayeDto.builder()
            .annee(annee)
            .mois(mois)
            .agence(agence != null && !agence.isBlank() ? agence : null)
            .gestionnaire(gestionnaire != null && !gestionnaire.isBlank() ? gestionnaire : null)
            .statut(statut != null && !statut.isEmpty() ? StatutImpaye.valueOf(statut) : null)
            .build();

        List<ImpayeDto> impayes = impayeService.getImpaYesForExport(filtre);
        byte[] excelData = excelExportService.exporterImpaYes(impayes);

        String filename = "impayes_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);
        response.getOutputStream().write(excelData);
    }

    @GetMapping("/export-pdf")
    @PreAuthorize("isAuthenticated()")
    public void exporterPdf(
            @RequestParam(required = false) Integer annee,
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) String agence,
            @RequestParam(required = false) String gestionnaire,
            @RequestParam(required = false) String statut,
            HttpServletResponse response) throws IOException {

        FiltreImpayeDto filtre = FiltreImpayeDto.builder()
            .annee(annee)
            .mois(mois)
            .agence(agence != null && !agence.isBlank() ? agence : null)
            .gestionnaire(gestionnaire != null && !gestionnaire.isBlank() ? gestionnaire : null)
            .statut(statut != null && !statut.isEmpty() ? StatutImpaye.valueOf(statut) : null)
            .build();

        List<ImpayeDto> impayes = impayeService.getImpaYesForExport(filtre);
        byte[] pdfData = pdfExportService.exporterImpayes(impayes);

        String filename = "impayes_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf";
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);
        response.getOutputStream().write(pdfData);
    }

    private List<String[]> getMoisList() {
        return List.of(
            new String[]{"1", "Janvier"}, new String[]{"2", "Février"},
            new String[]{"3", "Mars"}, new String[]{"4", "Avril"},
            new String[]{"5", "Mai"}, new String[]{"6", "Juin"},
            new String[]{"7", "Juillet"}, new String[]{"8", "Août"},
            new String[]{"9", "Septembre"}, new String[]{"10", "Octobre"},
            new String[]{"11", "Novembre"}, new String[]{"12", "Décembre"}
        );
    }
}
