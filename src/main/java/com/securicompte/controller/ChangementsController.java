package com.securicompte.controller;

import com.securicompte.dto.ChangementClientDto;
import com.securicompte.enums.Reseau;
import com.securicompte.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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
            @RequestParam(required = false) String reseau,
            @RequestParam(required = false, defaultValue = "false") boolean toutVoir,
            Model model) {

        List<ChangementClientDto> clients = toutVoir
            ? importService.getChangementsClientTous()
            : importService.getChangementsClientRecents();

        if (reseau != null && !reseau.isBlank()) {
            clients = clients.stream()
                .filter(c -> reseau.equalsIgnoreCase(c.getReseau() != null ? c.getReseau().name() : "BNI"))
                .collect(Collectors.toList());
        }

        model.addAttribute("changementsClient", clients);
        model.addAttribute("reseauFiltre", reseau != null ? reseau : "");
        model.addAttribute("toutVoir", toutVoir);
        return "changements/index";
    }

    @GetMapping("/clients")
    @PreAuthorize("isAuthenticated()")
    public String rechercherChangementsClient(
            @RequestParam(required = false) String recherche,
            @RequestParam(required = false) Integer annee,
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) String champ,
            @RequestParam(required = false) String reseau,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        Reseau reseauEnum = (reseau != null && !reseau.isBlank()) ? Reseau.valueOf(reseau) : null;
        var resultats = importService.rechercherChangementsClient(recherche, annee, mois, champ, reseauEnum, page);
        model.addAttribute("resultats", resultats);
        model.addAttribute("recherche", recherche != null ? recherche : "");
        model.addAttribute("anneeFiltre", annee);
        model.addAttribute("moisFiltre", mois);
        model.addAttribute("champFiltre", champ != null ? champ : "");
        model.addAttribute("reseauFiltre", reseau != null ? reseau : "");
        model.addAttribute("anneesDisponibles", importService.getAnneesChangementsClient());
        return "changements/clients";
    }

    @GetMapping("/clients/{clientId}/historique")
    @PreAuthorize("isAuthenticated()")
    @ResponseBody
    public List<ChangementClientDto> getHistoriqueClient(@PathVariable Long clientId) {
        return importService.getHistoriqueChangementsClient(clientId);
    }
}
