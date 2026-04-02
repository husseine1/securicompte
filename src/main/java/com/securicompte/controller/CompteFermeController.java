package com.securicompte.controller;

import com.securicompte.service.CompteFermeImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/compte-ferme")
@RequiredArgsConstructor
public class CompteFermeController {

    private final CompteFermeImportService compteFermeImportService;

    @GetMapping("/import")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_AGENT')")
    public String pagImport(Model model) {
        model.addAttribute("imports", compteFermeImportService.getTousLesImports());
        model.addAttribute("nbEnCours", compteFermeImportService.countImportsEnCours());
        return "compte-ferme/import";
    }

    @PostMapping("/import/upload")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_AGENT')")
    public String upload(@RequestParam("fichier") MultipartFile fichier,
                         Authentication auth,
                         RedirectAttributes ra) {
        if (fichier.isEmpty()) {
            ra.addFlashAttribute("erreur", "Veuillez sélectionner un fichier.");
            return "redirect:/compte-ferme/import";
        }
        try {
            compteFermeImportService.importerFichierAsync(
                fichier.getBytes(), fichier.getOriginalFilename(), auth.getName());
            ra.addFlashAttribute("succes", "Traitement lancé pour \u00ab\u00a0" + fichier.getOriginalFilename() + "\u00a0\u00bb.");
        } catch (Exception e) {
            ra.addFlashAttribute("erreur", "Erreur : " + e.getMessage());
        }
        return "redirect:/compte-ferme/import";
    }

    @GetMapping("/import/{id}")
    @PreAuthorize("isAuthenticated()")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("imp", compteFermeImportService.getById(id));
        return "compte-ferme/detail-import";
    }

    @PostMapping("/import/{id}/supprimer")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public String supprimer(@PathVariable Long id, RedirectAttributes ra) {
        try {
            compteFermeImportService.supprimerImport(id);
            ra.addFlashAttribute("succes", "Import supprimé.");
        } catch (Exception e) {
            ra.addFlashAttribute("erreur", e.getMessage());
        }
        return "redirect:/compte-ferme/import";
    }

    @PostMapping("/client/{clientId}/annuler")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public String annulerFermeture(@PathVariable Long clientId, RedirectAttributes ra) {
        compteFermeImportService.annulerFermeture(clientId);
        ra.addFlashAttribute("succes", "Fermeture de compte annulée.");
        return "redirect:/clients/" + clientId;
    }
}
