package com.securicompte.controller;

import com.securicompte.dto.ClientDetailDto;
import com.securicompte.dto.ClientDto;
import com.securicompte.service.ClientService;
import com.securicompte.service.ExcelExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;
    private final ExcelExportService excelExportService;

    @GetMapping("/recherche")
    @PreAuthorize("isAuthenticated()")
    public String rechercheClient(
            @RequestParam(value = "q", required = false, defaultValue = "") String recherche,
            @RequestParam(value = "agence", required = false, defaultValue = "") String agence,
            @RequestParam(value = "gestionnaire", required = false, defaultValue = "") String gestionnaire,
            @RequestParam(value = "sinistre", required = false, defaultValue = "false") boolean sinistre,
            @RequestParam(value = "compteFerme", required = false, defaultValue = "false") boolean compteFerme,
            @RequestParam(value = "annee", required = false, defaultValue = "0") int annee,
            @RequestParam(value = "mois", required = false, defaultValue = "0") int mois,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Model model) {

        boolean hasFilter = !recherche.isEmpty() || !agence.isEmpty() || !gestionnaire.isEmpty()
                         || sinistre || compteFerme || annee > 0;
        if (hasFilter) {
            var clients = clientService.rechercherClients(
                recherche, agence, gestionnaire, sinistre, compteFerme, annee, mois, page, 20);
            model.addAttribute("clients", clients);
            model.addAttribute("totalPages", clients.getTotalPages());
            model.addAttribute("totalElements", clients.getTotalElements());
        }
        model.addAttribute("recherche", recherche);
        model.addAttribute("agence", agence);
        model.addAttribute("gestionnaire", gestionnaire);
        model.addAttribute("sinistre", sinistre);
        model.addAttribute("compteFerme", compteFerme);
        model.addAttribute("annee", annee);
        model.addAttribute("mois", mois);
        model.addAttribute("page", page);
        model.addAttribute("agences", clientService.getAgences());
        model.addAttribute("gestionnaires", clientService.getGestionnaires());
        return "client/recherche";
    }

    @GetMapping("/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportClients(
            @RequestParam(value = "q", required = false, defaultValue = "") String recherche,
            @RequestParam(value = "agence", required = false, defaultValue = "") String agence,
            @RequestParam(value = "gestionnaire", required = false, defaultValue = "") String gestionnaire,
            @RequestParam(value = "sinistre", required = false, defaultValue = "false") boolean sinistre,
            @RequestParam(value = "compteFerme", required = false, defaultValue = "false") boolean compteFerme,
            @RequestParam(value = "annee", required = false, defaultValue = "0") int annee,
            @RequestParam(value = "mois", required = false, defaultValue = "0") int mois) {
        try {
            List<ClientDto> clients = clientService.exporterClients(
                recherche, agence, gestionnaire, sinistre, compteFerme, annee, mois);
            byte[] data = excelExportService.exporterClients(clients);
            String filename = "clients_" + LocalDate.now() + ".xlsx";
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public String detailClient(@PathVariable Long id, Model model) {
        ClientDetailDto client = clientService.getClientDetail(id);
        model.addAttribute("client", client);
        return "client/detail";
    }

    @PostMapping("/{id}/sinistre")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_AGENT')")
    public String enregistrerSinistre(
            @PathVariable Long id,
            @RequestParam(required = false) String dateSinistre,
            RedirectAttributes ra) {
        clientService.enregistrerSinistre(id, dateSinistre);
        if (dateSinistre != null && !dateSinistre.isBlank()) {
            ra.addFlashAttribute("succes", "Sinistre enregistré au " + dateSinistre + ".");
        } else {
            ra.addFlashAttribute("succes", "Sinistre supprimé.");
        }
        return "redirect:/clients/" + id;
    }
}
