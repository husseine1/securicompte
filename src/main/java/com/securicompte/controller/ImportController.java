package com.securicompte.controller;

import com.securicompte.dto.ImportResultDto;
import com.securicompte.entity.ImportFichier;
import com.securicompte.entity.User;
import com.securicompte.service.ImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/import")
@RequiredArgsConstructor
@Slf4j
public class ImportController {

    private final ImportService importService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public String pageImport(Model model) {
        model.addAttribute("imports", importService.getTousLesImports());
        model.addAttribute("anneeCourante", LocalDate.now().getYear());
        model.addAttribute("moisCourant", LocalDate.now().getMonthValue());
        return "import/index";
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public String uploadFichier(
            @RequestParam("fichier") MultipartFile fichier,
            @RequestParam("annee") Integer annee,
            @RequestParam("mois") Integer mois,
            @AuthenticationPrincipal User currentUser,
            RedirectAttributes redirectAttributes) {

        if (fichier.isEmpty()) {
            redirectAttributes.addFlashAttribute("erreur", "Veuillez sélectionner un fichier Excel.");
            return "redirect:/import";
        }

        String filename = fichier.getOriginalFilename();
        if (filename == null ||
            (!filename.endsWith(".xlsx") && !filename.endsWith(".xls") && !filename.endsWith(".xlsb"))) {
            redirectAttributes.addFlashAttribute("erreur", "Le fichier doit être au format Excel (.xlsx, .xls ou .xlsb)");
            return "redirect:/import";
        }

        try {
            ImportResultDto result = importService.importerFichier(fichier, annee, mois, currentUser);
            if (result.isSucces()) {
                redirectAttributes.addFlashAttribute("succes",
                    String.format("Import réussi ! Stock: %d clients, %d impayés détectés",
                        result.getNbStock(), result.getNbImpaYesDetectes()));
            } else {
                redirectAttributes.addFlashAttribute("erreur",
                    "Erreur lors de l'import: " + result.getMessageErreur());
            }
            redirectAttributes.addFlashAttribute("dernierImport", result);
        } catch (Exception e) {
            log.error("Erreur import fichier", e);
            redirectAttributes.addFlashAttribute("erreur", "Erreur inattendue: " + e.getMessage());
        }

        return "redirect:/import";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public String detailImport(@PathVariable Long id, Model model) {
        model.addAttribute("importFichier", importService.getImportById(id));
        return "import/detail";
    }

    @PostMapping("/{id}/supprimer")
    @PreAuthorize("hasRole('ADMIN')")
    public String supprimerImport(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            ImportFichier imp = importService.getImportById(id);
            String periode = imp.getMois() + "/" + imp.getAnnee();
            importService.supprimerImport(id);
            redirectAttributes.addFlashAttribute("succes",
                "Suppression réussie — l'import " + periode + " et toutes ses données ont été retirés.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erreur", "Erreur lors de la suppression : " + e.getMessage());
        }
        return "redirect:/import";
    }

    @GetMapping("/historique")
    public String historique() {
        return "redirect:/import";
    }
}
