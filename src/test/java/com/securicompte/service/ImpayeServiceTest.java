package com.securicompte.service;

import com.securicompte.dto.DashboardStatsDto;
import com.securicompte.entity.*;
import com.securicompte.enums.StatutImpaye;
import com.securicompte.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImpayeService - gestion des statuts et statistiques")
class ImpayeServiceTest {

    @Mock private ImpayeRepository          impayeRepository;
    @Mock private ClientService             clientService;
    @Mock private ImportFichierRepository   importFichierRepository;

    @InjectMocks
    private ImpayeService impayeService;

    private Client client;
    private User   admin;
    private Impaye impaye;

    @BeforeEach
    void setUp() {
        client = Client.builder().id(1L).numeroClient("C001").nom("Alice").build();
        admin  = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        impaye = Impaye.builder()
            .id(100L).client(client).annee(2024).mois(3)
            .statut(StatutImpaye.IMPAYE).build();
    }

    // ── regulariser ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("regulariser() - impayé existant → statut REGULARISE + commentaire + régularisePar")
    void regulariser_impayeExistant_statutRegularise() {
        when(impayeRepository.findById(100L)).thenReturn(Optional.of(impaye));
        when(impayeRepository.save(any())).thenReturn(impaye);

        boolean result = impayeService.regulariser(100L, "Paiement reçu", admin);

        assertThat(result).isTrue();
        assertThat(impaye.getStatut()).isEqualTo(StatutImpaye.REGULARISE);
        assertThat(impaye.getCommentaire()).isEqualTo("Paiement reçu");
        assertThat(impaye.getRegularisePar()).isEqualTo(admin);
        assertThat(impaye.getDateRegularisation()).isNotNull();
    }

    @Test
    @DisplayName("regulariser() - impayé inexistant → retourne false, aucun enregistrement")
    void regulariser_impayeInexistant_retourneFalse() {
        when(impayeRepository.findById(999L)).thenReturn(Optional.empty());

        boolean result = impayeService.regulariser(999L, "commentaire", admin);

        assertThat(result).isFalse();
        verify(impayeRepository, never()).save(any());
    }

    @Test
    @DisplayName("regulariser() - commentaire null → accepté, enregistré null")
    void regulariser_commentaireNull_accepte() {
        when(impayeRepository.findById(100L)).thenReturn(Optional.of(impaye));
        when(impayeRepository.save(any())).thenReturn(impaye);

        boolean result = impayeService.regulariser(100L, null, admin);

        assertThat(result).isTrue();
        assertThat(impaye.getCommentaire()).isNull();
        assertThat(impaye.getStatut()).isEqualTo(StatutImpaye.REGULARISE);
    }

    @Test
    @DisplayName("regulariser() - save() appelé exactement une fois")
    void regulariser_saveAppeleUneSeuleFois() {
        when(impayeRepository.findById(100L)).thenReturn(Optional.of(impaye));
        when(impayeRepository.save(any())).thenReturn(impaye);

        impayeService.regulariser(100L, null, admin);

        verify(impayeRepository, times(1)).save(impaye);
    }

    // ── marquerImpaye ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("marquerImpaye() - impayé régularisé → remis à IMPAYE, dates et commentaire effacés")
    void marquerImpaye_regularise_remisAImpaye() {
        impaye.setStatut(StatutImpaye.REGULARISE);
        impaye.setRegularisePar(admin);
        impaye.setDateRegularisation(java.time.LocalDateTime.now());
        impaye.setCommentaire("payé");

        when(impayeRepository.findById(100L)).thenReturn(Optional.of(impaye));
        when(impayeRepository.save(any())).thenReturn(impaye);

        boolean result = impayeService.marquerImpaye(100L, admin);

        assertThat(result).isTrue();
        assertThat(impaye.getStatut()).isEqualTo(StatutImpaye.IMPAYE);
        assertThat(impaye.getRegularisePar()).isNull();
        assertThat(impaye.getDateRegularisation()).isNull();
        assertThat(impaye.getCommentaire()).isNull();
    }

    @Test
    @DisplayName("marquerImpaye() - impayé inexistant → retourne false")
    void marquerImpaye_inexistant_retourneFalse() {
        when(impayeRepository.findById(999L)).thenReturn(Optional.empty());

        boolean result = impayeService.marquerImpaye(999L, admin);

        assertThat(result).isFalse();
        verify(impayeRepository, never()).save(any());
    }

    // ── getDashboardStats ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getDashboardStats() - 3 impayés + 1 régularisé → taux régularisation 25%")
    void getDashboardStats_tauxRegularisationCalculeCorrectement() {
        when(impayeRepository.countParStatut()).thenReturn(List.<Object[]>of(
            new Object[]{"IMPAYE",    3L},
            new Object[]{"REGULARISE", 1L}
        ));
        when(impayeRepository.countClientsAvecImpayes()).thenReturn(2L);
        when(impayeRepository.findClientsAvecPlusImpayes(StatutImpaye.IMPAYE, null))
            .thenReturn(List.<Object[]>of());

        DashboardStatsDto stats = impayeService.getDashboardStats(null);

        assertThat(stats.getTotalImpayes()).isEqualTo(3L);
        assertThat(stats.getTotalRegularises()).isEqualTo(1L);
        assertThat(stats.getTauxRegularisation()).isEqualTo(25.0);
        assertThat(stats.getTotalClientsAvecImpayes()).isEqualTo(2L);
    }

    @Test
    @DisplayName("getDashboardStats() - aucun impayé → taux 0% et top10 vide")
    void getDashboardStats_aucunImpaye_tauxZeroEtTop10Vide() {
        when(impayeRepository.countParStatut()).thenReturn(List.<Object[]>of());
        when(impayeRepository.countClientsAvecImpayes()).thenReturn(0L);
        when(impayeRepository.findClientsAvecPlusImpayes(StatutImpaye.IMPAYE, null))
            .thenReturn(List.<Object[]>of());

        DashboardStatsDto stats = impayeService.getDashboardStats(null);

        assertThat(stats.getTauxRegularisation()).isEqualTo(0.0);
        assertThat(stats.getTop10Clients()).isEmpty();
    }

    @Test
    @DisplayName("getDashboardStats() - 100% régularisés → taux 100%")
    void getDashboardStats_tousRegularises_tauxCent() {
        when(impayeRepository.countParStatut()).thenReturn(List.<Object[]>of(
            new Object[]{"REGULARISE", 5L}
        ));
        when(impayeRepository.countClientsAvecImpayes()).thenReturn(0L);
        when(impayeRepository.findClientsAvecPlusImpayes(StatutImpaye.IMPAYE, null))
            .thenReturn(List.<Object[]>of());

        DashboardStatsDto stats = impayeService.getDashboardStats(null);

        assertThat(stats.getTotalImpayes()).isEqualTo(0L);
        assertThat(stats.getTotalRegularises()).isEqualTo(5L);
        assertThat(stats.getTauxRegularisation()).isEqualTo(100.0);
    }
}
