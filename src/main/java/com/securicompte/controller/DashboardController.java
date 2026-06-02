package com.securicompte.controller;

import com.securicompte.dto.DashboardStatsDto;
import com.securicompte.dto.ImportResultDto;
import com.securicompte.enums.Reseau;
import com.securicompte.repository.ClientRepository;
import com.securicompte.repository.ImportFichierRepository;
import com.securicompte.service.ImpayeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final ImpayeService impayeService;
    private final ClientRepository clientRepository;
    private final ImportFichierRepository importFichierRepository;

    @GetMapping({"/", "/dashboard"})
    @PreAuthorize("isAuthenticated()")
    public String dashboard(@org.springframework.web.bind.annotation.RequestParam(required = false) String reseau,
                            Model model) {
        Reseau reseauEnum = null;
        if (reseau != null && !reseau.isBlank()) {
            try { reseauEnum = Reseau.valueOf(reseau.toUpperCase()); } catch (IllegalArgumentException ignored) {}
        }

        DashboardStatsDto stats = impayeService.getDashboardStats(reseauEnum);

        // Comptage clients par réseau
        long clientsBni = 0, clientsSib = 0;
        for (Object[] row : clientRepository.countByReseau()) {
            if (row[0] == null) continue;
            if (Reseau.BNI.name().equals(row[0].toString())) clientsBni = ((Number) row[1]).longValue();
            if (Reseau.SIB.name().equals(row[0].toString())) clientsSib = ((Number) row[1]).longValue();
        }
        stats.setTotalClients(clientsBni + clientsSib);
        stats.setClientsBni(clientsBni);
        stats.setClientsSib(clientsSib);
        stats.setTotalImportsFaits(importFichierRepository.count());
        stats.setDerniersImports(
            importFichierRepository.findTop5ByOrderByDateImportDesc().stream()
                .map(i -> ImportResultDto.builder()
                    .importId(i.getId())
                    .nomFichier(i.getNomFichier())
                    .annee(i.getAnnee())
                    .mois(i.getMois())
                    .reseau(i.getReseau() != null ? i.getReseau().name() : "BNI")
                    .statut(i.getStatut().name())
                    .nbStock(i.getNbStock())
                    .nbNouvelles(i.getNbNouvelles())
                    .dateImport(i.getDateImport())
                    .build())
                .toList()
        );
        model.addAttribute("stats", stats);
        model.addAttribute("reseauFiltre", reseau != null ? reseau : "");
        model.addAttribute("annees", importFichierRepository.findDistinctAnnees());
        return "dashboard/index";
    }
}
