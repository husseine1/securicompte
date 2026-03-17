package com.securicompte.service;

import com.securicompte.dto.*;
import com.securicompte.entity.Impaye;
import com.securicompte.enums.StatutImpaye;
import com.securicompte.repository.ImpayeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
        PageRequest pageRequest = PageRequest.of(filtre.getPage(), filtre.getSize());

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
        long totalImpayes = impayeRepository.countImpayes();
        long totalClientsAvecImpayes = impayeRepository.countClientsAvecImpayes();

        List<StatMoisDto> statsParMois = impayeRepository.countImpaYesParMois().stream()
            .limit(12)
            .filter(row -> row[0] != null && row[1] != null)
            .map(row -> StatMoisDto.builder()
                .annee(((Number) row[0]).intValue())
                .mois(((Number) row[1]).intValue())
                .moisNom(getMoisNom(((Number) row[1]).intValue()))
                .nbImpayes(row[2] != null ? ((Number) row[2]).longValue() : 0L)
                .build())
            .collect(Collectors.toList());

        List<StatAgenceDto> statsParAgence = impayeRepository.countImpaYesParAgence(null).stream()
            .limit(10)
            .filter(row -> row[1] != null)
            .map(row -> StatAgenceDto.builder()
                .agence(row[0] != null ? row[0].toString() : "Non défini")
                .nbImpayes(((Number) row[1]).longValue())
                .build())
            .collect(Collectors.toList());

        return DashboardStatsDto.builder()
            .totalImpayes(totalImpayes)
            .totalClientsAvecImpayes(totalClientsAvecImpayes)
            .statsParMois(statsParMois)
            .statsParAgence(statsParAgence)
            .build(); // totalClients et totalImportsFaits sont renseignés par DashboardController
    }

    @Transactional
    public boolean regulariser(Long impayeId, String commentaire, com.securicompte.entity.User regularisePar) {
        return impayeRepository.findById(impayeId).map(impaye -> {
            impaye.setStatut(StatutImpaye.REGULARISE);
            impaye.setDateRegularisation(java.time.LocalDateTime.now());
            impaye.setCommentaire(commentaire);
            impaye.setRegularisePar(regularisePar);
            impayeRepository.save(impaye);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean marquerImpaye(Long impayeId, com.securicompte.entity.User modifiePar) {
        return impayeRepository.findById(impayeId).map(impaye -> {
            impaye.setStatut(StatutImpaye.IMPAYE);
            impaye.setDateRegularisation(null);
            impaye.setRegularisePar(null);
            impaye.setCommentaire(null);
            impayeRepository.save(impaye);
            log.info("Impayé {} remis en statut IMPAYÉ par {}", impayeId, modifiePar.getUsername());
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
