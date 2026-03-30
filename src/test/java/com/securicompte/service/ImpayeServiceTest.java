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

    @Mock private ImpayeRepository       impayeRepository;
    @Mock private ClientService          clientService;
    @Mock private StockMensuelRepository stockMensuelRepository;
    @Mock private NotificationService    notificationService;

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
        when(stockMensuelRepository.findByClientIdAndAnneeAndMois(anyLong(), anyInt(), anyInt()))
            .thenReturn(Optional.empty());

        boolean result = impayeService.regulariser(100L, "Paiement reçu", admin);

        assertThat(result).isTrue();
        assertThat(impaye.getStatut()).isEqualTo(StatutImpaye.REGULARISE);
        assertThat(impaye.getCommentaire()).isEqualTo("Paiement reçu");
        assertThat(impaye.getRegularisePar()).isEqualTo(admin);
        assertThat(impaye.getDateRegularisation()).isNotNull();
        verify(notificationService, never()).creerNotificationChangementPrime(any(), any(), any(), any());
    }

    @Test
    @DisplayName("regulariser() - impayé inexistant → retourne false, aucun enregistrement")
    void regulariser_impayeInexistant_retourneFalse() {
        when(impayeRepository.findById(999L)).thenReturn(Optional.empty());

        boolean result = impayeService.regulariser(999L, "commentaire", admin);

        assertThat(result).isFalse();
        verify(impayeRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("regulariser() - primes identiques → aucune notification créée")
    void regulariser_primesIdentiques_aucuneNotification() {
        when(impayeRepository.findById(100L)).thenReturn(Optional.of(impaye));
        when(impayeRepository.save(any())).thenReturn(impaye);

        StockMensuel stock = StockMensuel.builder().client(client).securicompte("SC-IDENT").build();
        // Les deux appels retournent des valeurs identiques → pas de changement de prime
        when(stockMensuelRepository.findByClientIdAndAnneeAndMois(anyLong(), anyInt(), anyInt()))
            .thenReturn(Optional.of(stock));

        impayeService.regulariser(100L, null, admin);

        verify(notificationService, never()).creerNotificationChangementPrime(any(), any(), any(), any());
    }

    @Test
    @DisplayName("regulariser() - changement de securicompte détecté → notification créée")
    void regulariser_changementSecuricompte_notificationCreee() {
        when(impayeRepository.findById(100L)).thenReturn(Optional.of(impaye));
        when(impayeRepository.save(any())).thenReturn(impaye);

        StockMensuel stockOriginal = StockMensuel.builder().client(client).securicompte("SC-A").build();
        StockMensuel stockActuel   = StockMensuel.builder().client(client).securicompte("SC-B").build();
        // 1er appel → stockOriginal (mois de l'impayé), 2e appel → stockActuel (mois courant)
        when(stockMensuelRepository.findByClientIdAndAnneeAndMois(anyLong(), anyInt(), anyInt()))
            .thenReturn(Optional.of(stockOriginal))
            .thenReturn(Optional.of(stockActuel));

        impayeService.regulariser(100L, null, admin);

        verify(notificationService).creerNotificationChangementPrime(
            eq(impaye), any(StockMensuel.class), any(StockMensuel.class), eq("admin"));
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
        when(impayeRepository.countImpaYesParMois()).thenReturn(List.<Object[]>of());
        when(impayeRepository.countImpaYesParAgence(null)).thenReturn(List.<Object[]>of());
        when(impayeRepository.findClientsAvecPlusImpayes(StatutImpaye.IMPAYE)).thenReturn(List.<Object[]>of());

        DashboardStatsDto stats = impayeService.getDashboardStats();

        assertThat(stats.getTotalImpayes()).isEqualTo(3L);
        assertThat(stats.getTotalRegularises()).isEqualTo(1L);
        assertThat(stats.getTauxRegularisation()).isEqualTo(25.0);
        assertThat(stats.getTotalClientsAvecImpayes()).isEqualTo(2L);
    }

    @Test
    @DisplayName("getDashboardStats() - aucun impayé → taux 0% et listes vides")
    void getDashboardStats_aucunImpaye_tauxZeroEtListesVides() {
        when(impayeRepository.countParStatut()).thenReturn(List.<Object[]>of());
        when(impayeRepository.countClientsAvecImpayes()).thenReturn(0L);
        when(impayeRepository.countImpaYesParMois()).thenReturn(List.<Object[]>of());
        when(impayeRepository.countImpaYesParAgence(null)).thenReturn(List.<Object[]>of());
        when(impayeRepository.findClientsAvecPlusImpayes(StatutImpaye.IMPAYE)).thenReturn(List.<Object[]>of());

        DashboardStatsDto stats = impayeService.getDashboardStats();

        assertThat(stats.getTauxRegularisation()).isEqualTo(0.0);
        assertThat(stats.getStatsParMois()).isEmpty();
        assertThat(stats.getStatsParAgence()).isEmpty();
        assertThat(stats.getTop10Clients()).isEmpty();
    }

    @Test
    @DisplayName("getDashboardStats() - 100% régularisés → taux 100%")
    void getDashboardStats_tousRegularises_tauxCent() {
        when(impayeRepository.countParStatut()).thenReturn(List.<Object[]>of(
            new Object[]{"REGULARISE", 5L}
        ));
        when(impayeRepository.countClientsAvecImpayes()).thenReturn(0L);
        when(impayeRepository.countImpaYesParMois()).thenReturn(List.<Object[]>of());
        when(impayeRepository.countImpaYesParAgence(null)).thenReturn(List.<Object[]>of());
        when(impayeRepository.findClientsAvecPlusImpayes(StatutImpaye.IMPAYE)).thenReturn(List.<Object[]>of());

        DashboardStatsDto stats = impayeService.getDashboardStats();

        assertThat(stats.getTotalImpayes()).isEqualTo(0L);
        assertThat(stats.getTotalRegularises()).isEqualTo(5L);
        assertThat(stats.getTauxRegularisation()).isEqualTo(100.0);
    }
}
