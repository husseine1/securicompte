package com.securicompte.service;

import com.securicompte.dto.*;
import com.securicompte.entity.Impaye;
import com.securicompte.enums.StatutImpaye;
import com.securicompte.repository.ImpayeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImpayeService {

    private final ImpayeRepository impayeRepository;
    private final ClientService clientService;

    @Transactional(readOnly = true)
    public Page<ImpayeDto> getImpaYesWithFilters(FiltreImpayeDto filtre) {
        PageRequest pageRequest = PageRequest.of(
            filtre.getPage(), filtre.getSize(),
            Sort.by(Sort.Direction.DESC, "annee", "mois")
        );

        Page<Impaye> impayes = impayeRepository.findByFilters(
            filtre.getAnnee(),
            filtre.getMois(),
            filtre.getAgence(),
            filtre.getGestionnaire(),
            filtre.getStatut(),
            pageRequest
        );

        return impayes.map(clientService::toImpayeDto);
    }

    @Transactional(readOnly = true)
    public List<ImpayeDto> getImpaYesForExport(FiltreImpayeDto filtre) {
        List<Impaye> impayes = impayeRepository.findByFiltersForExport(
            filtre.getAnnee(),
            filtre.getMois(),
            filtre.getAgence(),
            filtre.getGestionnaire(),
            filtre.getStatut()
        );
        return impayes.stream().map(clientService::toImpayeDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStats() {
        long totalClients = 0; // à remplir
        long totalImpayes = impayeRepository.countImpayes();
        long totalClientsAvecImpayes = impayeRepository.countClientsAvecImpayes();

        List<StatMoisDto> statsParMois = impayeRepository.countImpaYesParMois().stream()
            .limit(12)
            .map(row -> StatMoisDto.builder()
                .annee((Integer) row[0])
                .mois((Integer) row[1])
                .moisNom(getMoisNom((Integer) row[1]))
                .nbImpayes((Long) row[2])
                .build())
            .collect(Collectors.toList());

        List<StatAgenceDto> statsParAgence = impayeRepository.countImpaYesParAgence(null).stream()
            .limit(10)
            .map(row -> StatAgenceDto.builder()
                .agence(row[0] != null ? row[0].toString() : "Non défini")
                .nbImpayes((Long) row[1])
                .build())
            .collect(Collectors.toList());

        return DashboardStatsDto.builder()
            .totalImpayes(totalImpayes)
            .totalClientsAvecImpayes(totalClientsAvecImpayes)
            .statsParMois(statsParMois)
            .statsParAgence(statsParAgence)
            .build();
    }

    @Transactional
    public boolean regulariser(Long impayeId, String commentaire) {
        return impayeRepository.findById(impayeId).map(impaye -> {
            impaye.setStatut(StatutImpaye.REGULARISE);
            impaye.setDateRegularisation(java.time.LocalDateTime.now());
            impaye.setCommentaire(commentaire);
            impayeRepository.save(impaye);
            return true;
        }).orElse(false);
    }

    public List<Integer> getAnnees() {
        return impayeRepository.findDistinctAnnees();
    }

    private static final String[] MOIS_NOMS = {
        "", "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
        "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"
    };

    private String getMoisNom(Integer mois) {
        if (mois != null && mois >= 1 && mois <= 12) return MOIS_NOMS[mois];
        return "";
    }
}
