package com.securicompte.service;

import com.securicompte.entity.Client;
import com.securicompte.entity.Impaye;
import com.securicompte.enums.Reseau;
import com.securicompte.enums.StatutImpaye;
import com.securicompte.repository.ClientRepository;
import com.securicompte.repository.ImpayeRepository;
import com.securicompte.repository.SouscriptionRepository;
import com.securicompte.repository.StockMensuelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImpayeDetectionService - détection automatique des impayés")
class ImpayeDetectionServiceTest {

    @Mock private ClientRepository        clientRepository;
    @Mock private SouscriptionRepository  souscriptionRepository;
    @Mock private StockMensuelRepository  stockMensuelRepository;
    @Mock private ImpayeRepository        impayeRepository;

    @InjectMocks
    private ImpayeDetectionService service;

    @Captor
    private ArgumentCaptor<List<Impaye>> impayeCaptor;

    private static final Reseau RESEAU = Reseau.BNI;

    private Client clientA;
    private Client clientB;

    @BeforeEach
    void setUp() {
        clientA = Client.builder().id(1L).numeroClient("C001").nom("Alice").build();
        clientB = Client.builder().id(2L).numeroClient("C002").nom("Bob").build();
    }

    @Test
    @DisplayName("Client absent du stock → impayé créé")
    void clientAbsentDuStock_doitCreerImpaye() {
        when(souscriptionRepository.findClientIdsWithSouscriptionBefore(any(LocalDate.class), any(Reseau.class)))
            .thenReturn(List.of(1L));
        when(clientRepository.findClientIdsWithSinistreInOrBefore(any(LocalDate.class), any(Reseau.class)))
            .thenReturn(List.of());
        when(stockMensuelRepository.findClientIdsPresentsDansMois(2024, 3, RESEAU))
            .thenReturn(List.of());
        when(impayeRepository.findClientIdsWithImpayeForMois(2024, 3))
            .thenReturn(List.of());
        when(clientRepository.findAllById(List.of(1L)))
            .thenReturn(List.of(clientA));
        when(impayeRepository.saveAll(any())).thenReturn(List.of());

        int nb = service.detecterImpaYesDuMois(2024, 3, RESEAU);

        assertThat(nb).isEqualTo(1);

        verify(impayeRepository).saveAll(impayeCaptor.capture());
        List<Impaye> impayes = impayeCaptor.getValue();
        assertThat(impayes).hasSize(1);
        assertThat(impayes.get(0).getClient()).isEqualTo(clientA);
        assertThat(impayes.get(0).getStatut()).isEqualTo(StatutImpaye.IMPAYE);
        assertThat(impayes.get(0).getAnnee()).isEqualTo(2024);
        assertThat(impayes.get(0).getMois()).isEqualTo(3);
    }

    @Test
    @DisplayName("Client présent dans le stock → aucun impayé créé")
    void clientPresentDansStock_neCreeRien() {
        when(souscriptionRepository.findClientIdsWithSouscriptionBefore(any(LocalDate.class), any(Reseau.class)))
            .thenReturn(List.of(1L));
        when(clientRepository.findClientIdsWithSinistreInOrBefore(any(LocalDate.class), any(Reseau.class)))
            .thenReturn(List.of());
        when(stockMensuelRepository.findClientIdsPresentsDansMois(2024, 3, RESEAU))
            .thenReturn(List.of(1L));
        when(impayeRepository.findClientIdsWithImpayeForMois(2024, 3))
            .thenReturn(List.of());

        int nb = service.detecterImpaYesDuMois(2024, 3, RESEAU);

        assertThat(nb).isEqualTo(0);
        verify(impayeRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Client avec sinistre → exclu de la détection")
    void clientAvecSinistre_estExclu() {
        when(souscriptionRepository.findClientIdsWithSouscriptionBefore(any(LocalDate.class), any(Reseau.class)))
            .thenReturn(List.of(1L, 2L));
        when(clientRepository.findClientIdsWithSinistreInOrBefore(any(LocalDate.class), any(Reseau.class)))
            .thenReturn(List.of(1L));
        when(stockMensuelRepository.findClientIdsPresentsDansMois(2024, 3, RESEAU))
            .thenReturn(List.of());
        when(impayeRepository.findClientIdsWithImpayeForMois(2024, 3))
            .thenReturn(List.of());
        when(clientRepository.findAllById(List.of(2L)))
            .thenReturn(List.of(clientB));
        when(impayeRepository.saveAll(any())).thenReturn(List.of());

        int nb = service.detecterImpaYesDuMois(2024, 3, RESEAU);

        assertThat(nb).isEqualTo(1);
        verify(impayeRepository).saveAll(impayeCaptor.capture());
        assertThat(impayeCaptor.getValue().get(0).getClient()).isEqualTo(clientB);
    }

    @Test
    @DisplayName("Impayé déjà existant → pas de doublon")
    void impayeDejaExistant_neCreeDoublon() {
        when(souscriptionRepository.findClientIdsWithSouscriptionBefore(any(LocalDate.class), any(Reseau.class)))
            .thenReturn(List.of(1L));
        when(clientRepository.findClientIdsWithSinistreInOrBefore(any(LocalDate.class), any(Reseau.class)))
            .thenReturn(List.of());
        when(stockMensuelRepository.findClientIdsPresentsDansMois(2024, 3, RESEAU))
            .thenReturn(List.of());
        when(impayeRepository.findClientIdsWithImpayeForMois(2024, 3))
            .thenReturn(List.of(1L));

        int nb = service.detecterImpaYesDuMois(2024, 3, RESEAU);

        assertThat(nb).isEqualTo(0);
        verify(impayeRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Aucune souscription → retourne 0 immédiatement")
    void aucuneSouscription_retourneZero() {
        when(souscriptionRepository.findClientIdsWithSouscriptionBefore(any(LocalDate.class), any(Reseau.class)))
            .thenReturn(List.of());

        int nb = service.detecterImpaYesDuMois(2024, 3, RESEAU);

        assertThat(nb).isEqualTo(0);
        verifyNoInteractions(stockMensuelRepository, impayeRepository);
        verify(clientRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("Tous les clients avec sinistre → retourne 0 après exclusion")
    void tousAvecSinistre_retourneZero() {
        when(souscriptionRepository.findClientIdsWithSouscriptionBefore(any(LocalDate.class), any(Reseau.class)))
            .thenReturn(List.of(1L, 2L));
        when(clientRepository.findClientIdsWithSinistreInOrBefore(any(LocalDate.class), any(Reseau.class)))
            .thenReturn(List.of(1L, 2L));

        int nb = service.detecterImpaYesDuMois(2024, 3, RESEAU);

        assertThat(nb).isEqualTo(0);
        verifyNoInteractions(stockMensuelRepository);
    }
}
