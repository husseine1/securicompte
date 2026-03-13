package com.securicompte.controller;

import com.securicompte.dto.ImportResultDto;
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

        if (!fichier.getOriginalFilename().endsWith(".xlsx") &&
            !fichier.getOriginalFilename().endsWith(".xls")) {
            redirectAttributes.addFlashAttribute("erreur", "Le fichier doit être au format Excel (.xlsx ou .xls)");
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

    @GetMapping("/historique")
    public String historique(Model model) {
        model.addAttribute("imports", importService.getTousLesImports());
        return "import/historique";
    }
}
