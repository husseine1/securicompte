package com.securicompte.controller;

import com.securicompte.dto.ChangementClientDto;
import com.securicompte.dto.ChangementPrimeDto;
import com.securicompte.enums.StatutChangement;
import com.securicompte.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.securicompte.entity.User;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/changements")
@RequiredArgsConstructor
public class ChangementsController {

    private final ImportService importService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String vueConsolidee(
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String reseau,
            @RequestParam(required = false, defaultValue = "false") boolean toutVoir,
            Model model) {

        List<ChangementPrimeDto> primes = importService.getChangementsEnAttente();
        List<ChangementClientDto> clients = toutVoir
            ? importService.getChangementsClientTous()
            : importService.getChangementsClientRecents();

        // Filtrer par réseau si demandé
        if (reseau != null && !reseau.isBlank()) {
            primes  = primes.stream()
                .filter(c -> reseau.equalsIgnoreCase(c.getReseau() != null ? c.getReseau().name() : "BNI"))
                .collect(Collectors.toList());
            clients = clients.stream()
                .filter(c -> reseau.equalsIgnoreCase(c.getReseau() != null ? c.getReseau().name() : "BNI"))
                .collect(Collectors.toList());
        }

        long nbEnAttente = primes.stream()
            .filter(c -> c.getStatut() == StatutChangement.EN_ATTENTE).count();

        model.addAttribute("primes", primes);
        model.addAttribute("changementsClient", clients);
        model.addAttribute("nbEnAttente", nbEnAttente);
        model.addAttribute("reseauFiltre", reseau != null ? reseau : "");
        model.addAttribute("toutVoir", toutVoir);
        return "changements/index";
    }

    @PostMapping("/prime/{id}/approuver")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @ResponseBody
    public java.util.Map<String, String> approuver(@PathVariable Long id,
                                                    @AuthenticationPrincipal User user) {
        importService.approuverChangement(id, user.getUsername());
        return java.util.Map.of("statut", "APPROUVE");
    }

    @PostMapping("/prime/{id}/refuser")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @ResponseBody
    public java.util.Map<String, String> refuser(@PathVariable Long id,
                                                  @AuthenticationPrincipal User user) {
        importService.refuserChangement(id, user.getUsername());
        return java.util.Map.of("statut", "REFUSE");
    }
}
