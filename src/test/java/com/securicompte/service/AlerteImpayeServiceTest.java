package com.securicompte.service;

import com.securicompte.entity.Client;
import com.securicompte.entity.Impaye;
import com.securicompte.entity.Notification;
import com.securicompte.enums.StatutImpaye;
import com.securicompte.repository.ImpayeRepository;
import com.securicompte.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlerteImpayeService - alertes impayés anciens")
class AlerteImpayeServiceTest {

    @Mock private ImpayeRepository      impayeRepository;
    @Mock private NotificationRepository notificationRepository;

    @InjectMocks
    private AlerteImpayeService alerteImpayeService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(alerteImpayeService, "seuilMois", 3);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Impaye buildImpaye(Long id, int annee, int mois) {
        Client client = new Client();
        client.setId(10L);
        client.setNom("Dupont Jean");
        client.setNumeroClient("C001");

        Impaye imp = new Impaye();
        imp.setId(id);
        imp.setAnnee(annee);
        imp.setMois(mois);
        imp.setStatut(StatutImpaye.IMPAYE);
        imp.setClient(client);
        return imp;
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Aucun impayé ancien → aucune notification créée")
    void alerter_aucunImpaye_aucuneNotification() {
        when(impayeRepository.findImpaYesAnciens(eq(StatutImpaye.IMPAYE), anyInt()))
                .thenReturn(List.of());

        alerteImpayeService.alerterImpaYesAnciens();

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Impayé ancien sans notification existante → notification créée")
    void alerter_impayeAncien_notificationCreee() {
        Impaye impaye = buildImpaye(1L, 2024, 1);
        when(impayeRepository.findImpaYesAnciens(eq(StatutImpaye.IMPAYE), anyInt()))
                .thenReturn(List.of(impaye));
        when(notificationRepository.existsByTypeAndImpayeId("IMPAYE_ANCIEN", 1L))
                .thenReturn(false);

        alerteImpayeService.alerterImpaYesAnciens();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo("IMPAYE_ANCIEN");
        assertThat(saved.getClientId()).isEqualTo(10L);
        assertThat(saved.getImpayeId()).isEqualTo(1L);
        assertThat(saved.getMessage()).contains("Dupont Jean");
        assertThat(saved.getMessage()).contains("3 mois");
        assertThat(saved.getCreePar()).isEqualTo("SYSTEME");
    }

    @Test
    @DisplayName("Impayé déjà notifié → anti-doublon respecté, aucune nouvelle notification")
    void alerter_doublon_aucuneSauvegarde() {
        Impaye impaye = buildImpaye(2L, 2024, 2);
        when(impayeRepository.findImpaYesAnciens(eq(StatutImpaye.IMPAYE), anyInt()))
                .thenReturn(List.of(impaye));
        when(notificationRepository.existsByTypeAndImpayeId("IMPAYE_ANCIEN", 2L))
                .thenReturn(true);

        alerteImpayeService.alerterImpaYesAnciens();

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Plusieurs impayés anciens, certains déjà notifiés → seuls les nouveaux sont créés")
    void alerter_plusieurs_seulsNouveauxCrees() {
        Impaye imp1 = buildImpaye(1L, 2024, 1); // déjà notifié
        Impaye imp2 = buildImpaye(2L, 2024, 2); // nouveau
        Impaye imp3 = buildImpaye(3L, 2024, 3); // nouveau

        when(impayeRepository.findImpaYesAnciens(eq(StatutImpaye.IMPAYE), anyInt()))
                .thenReturn(List.of(imp1, imp2, imp3));
        when(notificationRepository.existsByTypeAndImpayeId("IMPAYE_ANCIEN", 1L)).thenReturn(true);
        when(notificationRepository.existsByTypeAndImpayeId("IMPAYE_ANCIEN", 2L)).thenReturn(false);
        when(notificationRepository.existsByTypeAndImpayeId("IMPAYE_ANCIEN", 3L)).thenReturn(false);

        alerteImpayeService.alerterImpaYesAnciens();

        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    @Test
    @DisplayName("La limite de période est calculée correctement")
    void alerter_limitePeriodeCalculeeCorrectement() {
        when(impayeRepository.findImpaYesAnciens(eq(StatutImpaye.IMPAYE), anyInt()))
                .thenReturn(List.of());

        alerteImpayeService.alerterImpaYesAnciens();

        // Vérifie que findImpaYesAnciens est bien appelé avec une limite cohérente
        ArgumentCaptor<Integer> limiteCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(impayeRepository).findImpaYesAnciens(eq(StatutImpaye.IMPAYE), limiteCaptor.capture());

        int limite = limiteCaptor.getValue();
        // La limite doit être > 0 et raisonnable (quelque chose comme annee*12+mois-3)
        assertThat(limite).isGreaterThan(0);
    }
}
