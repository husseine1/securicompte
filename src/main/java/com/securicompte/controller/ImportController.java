package com.securicompte.controller;

import com.securicompte.dto.ChangementClientDto;
import com.securicompte.dto.ChangementPrimeDto;
import com.securicompte.dto.ImportResultDto;
import com.securicompte.dto.StatAgenceDto;
import com.securicompte.entity.ImportFichier;
import com.securicompte.entity.ImportFichierBytes;
import com.securicompte.entity.User;
import com.securicompte.enums.Reseau;
import com.securicompte.repository.ImpayeRepository;
import com.securicompte.service.ImportService;
import com.securicompte.service.ImpayeService;
import com.securicompte.service.PdfExportService;
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

    private final ImportService    importService;
    private final ImpayeService    impayeService;
    private final PdfExportService pdfExportService;
    private final ImpayeRepository impayeRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public String pageImport(Model model,
                             @RequestParam(required = false) Boolean done,
                             @RequestParam(required = false) Boolean supprime) {
        model.addAttribute("imports", importService.getTousLesImports());
        model.addAttribute("importsAvecFichier", importService.getIdsAvecFichier());
        model.addAttribute("nbPrimeEnAttente", importService.getNbPrimeEnAttenteParMois());
        model.addAttribute("nbChangementsClient", importService.getNbChangementsClientParMois());
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
            @RequestParam(value = "reseau", defaultValue = "BNI") String reseauStr,
            @RequestParam("fichier") MultipartFile fichier,
            @RequestParam(value = "fichierRenouvellement", required = false) MultipartFile fichierRenouvellement,
            @RequestParam("annee") Integer annee,
            @RequestParam("mois") Integer mois,
            @AuthenticationPrincipal User currentUser,
            RedirectAttributes redirectAttributes) {

        Reseau reseau;
        try {
            reseau = Reseau.valueOf(reseauStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("erreur", "Réseau invalide : " + reseauStr);
            return "redirect:/import";
        }

        if (fichier.isEmpty()) {
            redirectAttributes.addFlashAttribute("erreur", "Veuillez sélectionner un fichier Excel.");
            return "redirect:/import";
        }

        try {
            if (reseau == Reseau.SIB) {
                if (fichierRenouvellement == null || fichierRenouvellement.isEmpty()) {
                    redirectAttributes.addFlashAttribute("erreur",
                        "Pour un import SIB, veuillez fournir les deux fichiers (nouvelles souscriptions + renouvellement).");
                    return "redirect:/import";
                }
                byte[] bytesNouvelles      = fichier.getBytes();
                byte[] bytesRenouvellement = fichierRenouvellement.getBytes();
                importService.importerFichierSibAsync(
                    bytesNouvelles, bytesRenouvellement,
                    fichier.getOriginalFilename(), fichierRenouvellement.getOriginalFilename(),
                    fichier.getContentType(), annee, mois, currentUser);
            } else {
                String filename = fichier.getOriginalFilename();
                if (filename == null ||
                    (!filename.endsWith(".xlsx") && !filename.endsWith(".xls") && !filename.endsWith(".xlsb"))) {
                    redirectAttributes.addFlashAttribute("erreur", "Le fichier doit être au format Excel (.xlsx, .xls ou .xlsb)");
                    return "redirect:/import";
                }
                importService.importerFichierAsync(fichier.getBytes(), filename,
                    fichier.getContentType(), annee, mois, reseau, currentUser);
            }

            redirectAttributes.addFlashAttribute("succes",
                "Import " + reseau + " lancé en arrière-plan pour " + mois + "/" + annee +
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

    // ─── Rapport PDF mensuel ──────────────────────────────────────────────────

    @GetMapping("/{id}/rapport-pdf")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public ResponseEntity<byte[]> rapportPdf(@PathVariable Long id) {
        try {
            ImportFichier imp = importService.getImportById(id);
            Reseau reseau = imp.getReseau() != null ? imp.getReseau() : Reseau.BNI;

            com.securicompte.dto.FiltreImpayeDto filtre = com.securicompte.dto.FiltreImpayeDto.builder()
                .annee(imp.getAnnee()).mois(imp.getMois()).reseau(reseau).build();
            java.util.List<com.securicompte.dto.ImpayeDto> impayes = impayeService.getImpaYesForExport(filtre);

            java.util.List<StatAgenceDto> statsAgence = impayeRepository
                .countImpaYesParAgenceByReseau(imp.getAnnee(), reseau).stream()
                .limit(15)
                .filter(r -> r[1] != null)
                .map(r -> StatAgenceDto.builder()
                    .agence(r[0] != null ? r[0].toString() : "N/A")
                    .nbImpayes(((Number) r[1]).longValue())
                    .build())
                .collect(java.util.stream.Collectors.toList());

            byte[] pdf = pdfExportService.genererRapportMensuel(imp, impayes, statsAgence);
            String moisNoms[] = {"","Janvier","Fevrier","Mars","Avril","Mai","Juin",
                                  "Juillet","Aout","Septembre","Octobre","Novembre","Decembre"};
            String filename = "rapport_" + reseau + "_" + moisNoms[imp.getMois()] + "_" + imp.getAnnee() + ".pdf";
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
        } catch (Exception e) {
            log.error("Erreur génération rapport PDF: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
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
