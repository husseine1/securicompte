package com.securicompte.controller;

import com.securicompte.dto.ChangementClientDto;
import com.securicompte.dto.ChangementPrimeDto;
import com.securicompte.dto.ImportResultDto;
import com.securicompte.entity.ImportFichierBytes;
import com.securicompte.entity.User;
import com.securicompte.service.ImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/import")
@RequiredArgsConstructor
@Slf4j
public class ImportController {

    private final ImportService importService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public String pageImport(Model model,
                             @RequestParam(required = false) Boolean done,
                             @RequestParam(required = false) Boolean supprime) {
        model.addAttribute("imports", importService.getTousLesImports());
        model.addAttribute("importsAvecFichier", importService.getIdsAvecFichier());
        model.addAttribute("anneeCourante", LocalDate.now().getYear());
        model.addAttribute("moisCourant", LocalDate.now().getMonthValue());
        if (Boolean.TRUE.equals(done))
            model.addAttribute("succes", "Import terminé avec succès !");
        if (Boolean.TRUE.equals(supprime))
            model.addAttribute("succes", "Suppression terminée avec succès !");
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
            // Copier les bytes avant l'appel async (le MultipartFile devient invalide après la requête HTTP)
            byte[] fileBytes   = fichier.getBytes();
            String contentType = fichier.getContentType();

            importService.importerFichierAsync(fileBytes, filename, contentType, annee, mois, currentUser);
            redirectAttributes.addFlashAttribute("succes",
                "Import lancé en arrière-plan pour " + mois + "/" + annee +
                " — la page se rafraîchira automatiquement.");
        } catch (Exception e) {
            log.error("Erreur lancement import fichier", e);
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
            String periode = importService.preparerSuppression(id);
            importService.supprimerImportAsync(id);
            redirectAttributes.addFlashAttribute("succes",
                "Suppression de l'import " + periode + " lancée — la page se rafraîchira automatiquement.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erreur", "Erreur lors de la suppression : " + e.getMessage());
        }
        return "redirect:/import";
    }

    /** Retourne le nombre d'imports EN_COURS — utilisé par le polling JS de la barre de progression. */
    @GetMapping("/en-cours")
    @ResponseBody
    @PreAuthorize("isAuthenticated()")
    public Map<String, Long> importsEnCours() {
        return Map.of("count", importService.countImportsEnCours());
    }

    @GetMapping("/historique")
    public String historique() {
        return "redirect:/import";
    }

    /** Purge les clients sans aucune donnée (utile après suppression d'imports). */
    @PostMapping("/purger-clients-orphelins")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public Map<String, Object> purgerClientsOrphelins() {
        int nb = importService.purgerClientsOrphelins();
        log.info("Purge clients orphelins : {} client(s) supprimé(s)", nb);
        return Map.of("nbSupprimes", nb);
    }

    // ─── Fichiers Excel ───────────────────────────────────────────────────────

    @GetMapping("/{id}/fichier")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public ResponseEntity<byte[]> telechargerFichier(@PathVariable Long id) {
        return importService.getFichierBytes(id)
            .map(f -> ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + importService.getImportById(id).getNomFichier() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(f.getFichierBytes()))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/reimporter")
    @PreAuthorize("hasRole('ADMIN')")
    public String reimporter(@PathVariable Long id,
                              @AuthenticationPrincipal User user,
                              RedirectAttributes ra) {
        try {
            importService.reimporterDepuisBase(id, user);
            ra.addFlashAttribute("succes", "Ré-import lancé en arrière-plan.");
        } catch (Exception e) {
            ra.addFlashAttribute("erreur", "Erreur ré-import : " + e.getMessage());
        }
        return "redirect:/import";
    }

    @PostMapping("/{id}/supprimer-fichier")
    @PreAuthorize("hasRole('ADMIN')")
    public String supprimerFichier(@PathVariable Long id, RedirectAttributes ra) {
        importService.supprimerFichierBytes(id);
        ra.addFlashAttribute("succes", "Fichier supprimé de la base de données.");
        return "redirect:/import";
    }

    // ─── Changements de prime ─────────────────────────────────────────────────

    @GetMapping("/{annee}/{mois}/changements-prime")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public String changementsPrime(@PathVariable int annee, @PathVariable int mois, Model model) {
        model.addAttribute("changements", importService.getChangementsPrime(annee, mois));
        model.addAttribute("annee", annee);
        model.addAttribute("mois", mois);
        return "notifications/changements-prime";
    }

    @PostMapping("/changements/{id}/approuver")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public Map<String, Object> approuverChangement(@PathVariable Long id,
                                                    @AuthenticationPrincipal User user) {
        importService.approuverChangement(id, user.getUsername());
        return Map.of("statut", "APPROUVE");
    }

    @PostMapping("/changements/{id}/refuser")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public Map<String, Object> refuserChangement(@PathVariable Long id,
                                                  @AuthenticationPrincipal User user) {
        importService.refuserChangement(id, user.getUsername());
        return Map.of("statut", "REFUSE");
    }

    @PostMapping("/{annee}/{mois}/changements-prime/approuver-tous")
    @PreAuthorize("hasRole('ADMIN')")
    public String approuverTous(@PathVariable int annee, @PathVariable int mois,
                                 @AuthenticationPrincipal User user, RedirectAttributes ra) {
        int nb = importService.approuverTousChangements(annee, mois, user.getUsername());
        ra.addFlashAttribute("succes", nb + " changement(s) approuvé(s).");
        return "redirect:/import/" + annee + "/" + mois + "/changements-prime";
    }

    @PostMapping("/{annee}/{mois}/changements-prime/refuser-tous")
    @PreAuthorize("hasRole('ADMIN')")
    public String refuserTous(@PathVariable int annee, @PathVariable int mois,
                               @AuthenticationPrincipal User user, RedirectAttributes ra) {
        int nb = importService.refuserTousChangements(annee, mois, user.getUsername());
        ra.addFlashAttribute("succes", nb + " changement(s) refusé(s).");
        return "redirect:/import/" + annee + "/" + mois + "/changements-prime";
    }

    // ─── Changements données client ───────────────────────────────────────────

    @GetMapping("/{annee}/{mois}/changements-client")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public String changementsClient(@PathVariable int annee, @PathVariable int mois, Model model) {
        model.addAttribute("changements", importService.getChangementsClient(annee, mois));
        model.addAttribute("annee", annee);
        model.addAttribute("mois", mois);
        return "notifications/changements-client";
    }

}
