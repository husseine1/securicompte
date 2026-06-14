package com.securicompte.service;

import com.securicompte.dto.*;
import com.securicompte.entity.Impaye;
import com.securicompte.enums.Reseau;
import com.securicompte.enums.StatutImpaye;
import com.securicompte.repository.ImpayeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImpayeService {

    private final ImpayeRepository       impayeRepository;
    private final ClientService          clientService;

    @Transactional(readOnly = true)
    public Page<ImpayeDto> getImpaYesWithFilters(FiltreImpayeDto filtre) {
        PageRequest pageRequest = PageRequest.of(filtre.getPage(), filtre.getSize());

        Page<Impaye> impayes = impayeRepository.findByFilters(
            filtre.getAnnee(),
            filtre.getMois(),
            filtre.getAgence(),
            filtre.getGestionnaire(),
            filtre.getStatut(),
            filtre.getReseau(),
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
            filtre.getStatut(),
            filtre.getReseau()
        );
        return impayes.stream().map(clientService::toImpayeDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStats(Reseau reseau) {
        Map<String, Long> parStatut = new HashMap<>();
        impayeRepository.countParStatut().forEach(row -> {
            if (row[0] != null) parStatut.put(row[0].toString(), ((Number) row[1]).longValue());
        });
        long totalImpayes    = parStatut.getOrDefault("IMPAYE",    0L);
        long totalRegularises = parStatut.getOrDefault("REGULARISE", 0L);
        long totalComptable  = totalImpayes + totalRegularises;
        double tauxReg = totalComptable > 0 ? totalRegularises * 100.0 / totalComptable : 0.0;
        long totalClientsAvecImpayes = impayeRepository.countClientsAvecImpayes();

        List<Top10ClientDto> top10 = impayeRepository
            .findClientsAvecPlusImpayes(StatutImpaye.IMPAYE, reseau).stream()
            .limit(10)
            .map(row -> Top10ClientDto.builder()
                .nom(row[0] != null ? row[0].toString() : "-")
                .numeroClient(row[1] != null ? row[1].toString() : "-")
                .nbImpayes(((Number) row[2]).longValue())
                .build())
            .collect(Collectors.toList());

        return DashboardStatsDto.builder()
            .totalImpayes(totalImpayes)
            .totalRegularises(totalRegularises)
            .tauxRegularisation(tauxReg)
            .totalClientsAvecImpayes(totalClientsAvecImpayes)
            .top10Clients(top10)
            .build();
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

}
