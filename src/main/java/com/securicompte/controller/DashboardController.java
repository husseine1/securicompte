package com.securicompte.controller;

import com.securicompte.dto.DashboardStatsDto;
import com.securicompte.dto.ImportResultDto;
import com.securicompte.repository.ClientRepository;
import com.securicompte.repository.ImportFichierRepository;
import com.securicompte.service.ImpayeService;
import com.securicompte.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final ImpayeService impayeService;
    private final ImportService importService;
    private final ClientRepository clientRepository;
    private final ImportFichierRepository importFichierRepository;

    @GetMapping({"/", "/dashboard"})
    @PreAuthorize("isAuthenticated()")
    public String dashboard(Model model) {
        DashboardStatsDto stats = impayeService.getDashboardStats();
        stats.setTotalClients(clientRepository.count());
        stats.setTotalImportsFaits(importFichierRepository.count());
        stats.setDerniersImports(
            importService.getTousLesImports().stream()
                .limit(5)
                .map(i -> ImportResultDto.builder()
                    .importId(i.getId())
                    .nomFichier(i.getNomFichier())
                    .annee(i.getAnnee())
                    .mois(i.getMois())
                    .statut(i.getStatut().name())
                    .nbStock(i.getNbStock())
                    .nbNouvelles(i.getNbNouvelles())
                    .dateImport(i.getDateImport())
                    .build())
                .toList()
        );
        model.addAttribute("stats", stats);
        model.addAttribute("annees", importFichierRepository.findDistinctAnnees());
        return "dashboard/index";
    }
}
