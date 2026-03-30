package com.securicompte.controller;

import com.securicompte.entity.User;
import com.securicompte.service.SinistreImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/sinistre")
@RequiredArgsConstructor
@Slf4j
public class SinistreController {

    private final SinistreImportService sinistreImportService;

    @GetMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public String pageImport(Model model,
                             @RequestParam(required = false) Boolean done) {
        model.addAttribute("imports", sinistreImportService.getTousLesImports());
        if (Boolean.TRUE.equals(done))
            model.addAttribute("succes", "Import sinistres terminé avec succès !");
        return "sinistre/index";
    }

    @PostMapping("/import/upload")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public String uploadFichier(
            @RequestParam("fichier") MultipartFile fichier,
            @AuthenticationPrincipal User currentUser,
            RedirectAttributes redirectAttributes) {

        if (fichier.isEmpty()) {
            redirectAttributes.addFlashAttribute("erreur", "Veuillez sélectionner un fichier Excel.");
            return "redirect:/sinistre/import";
        }

        String filename = fichier.getOriginalFilename();
        if (filename == null ||
            (!filename.toLowerCase().endsWith(".xlsx") &&
             !filename.toLowerCase().endsWith(".xls"))) {
            redirectAttributes.addFlashAttribute("erreur", "Le fichier doit être au format Excel (.xlsx ou .xls)");
            return "redirect:/sinistre/import";
        }

        try {
            byte[] fileBytes = fichier.getBytes();
            String username = currentUser != null ? currentUser.getUsername() : "inconnu";
            sinistreImportService.importerFichierAsync(fileBytes, filename, username);
            redirectAttributes.addFlashAttribute("succes",
                "Import sinistres lancé — la page se rafraîchira automatiquement.");
        } catch (Exception e) {
            log.error("Erreur lancement import sinistres", e);
            redirectAttributes.addFlashAttribute("erreur", "Erreur inattendue : " + e.getMessage());
        }

        return "redirect:/sinistre/import";
    }

    @GetMapping("/import/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("imp", sinistreImportService.getById(id));
        return "sinistre/detail";
    }

    @PostMapping("/import/{id}/supprimer")
    @PreAuthorize("hasRole('ADMIN')")
    public String supprimer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            sinistreImportService.supprimerImport(id);
            redirectAttributes.addFlashAttribute("succes", "Import sinistre supprimé.");
        } catch (Exception e) {
            log.error("Erreur suppression import sinistre {}", id, e);
            redirectAttributes.addFlashAttribute("erreur", "Erreur : " + e.getMessage());
        }
        return "redirect:/sinistre/import";
    }

    @PostMapping("/client/{clientId}/annuler")
    @PreAuthorize("hasRole('ADMIN')")
    public String annulerSinistre(@PathVariable Long clientId,
                                   RedirectAttributes redirectAttributes) {
        try {
            sinistreImportService.annulerSinistre(clientId);
            redirectAttributes.addFlashAttribute("succes", "Sinistre annulé et impayés recalculés.");
        } catch (Exception e) {
            log.error("Erreur annulation sinistre client {}", clientId, e);
            redirectAttributes.addFlashAttribute("erreur", "Erreur : " + e.getMessage());
        }
        return "redirect:/clients/" + clientId;
    }

    @GetMapping("/import/en-cours")
    @ResponseBody
    @PreAuthorize("isAuthenticated()")
    public Map<String, Long> importsEnCours() {
        return Map.of("count", sinistreImportService.countImportsEnCours());
    }
}
