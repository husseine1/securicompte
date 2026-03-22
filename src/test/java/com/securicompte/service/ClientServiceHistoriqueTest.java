package com.securicompte.service;

import com.securicompte.dto.ClientDetailDto;
import com.securicompte.dto.HistoriquePaiementDto;
import com.securicompte.dto.ImpayeDto;
import com.securicompte.entity.*;
import com.securicompte.enums.StatutImpaye;
import com.securicompte.mapper.ImpayeMapper;
import com.securicompte.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClientService - construireHistorique")
class ClientServiceHistoriqueTest {

    @Mock private ClientRepository        clientRepository;
    @Mock private SouscriptionRepository  souscriptionRepository;
    @Mock private StockMensuelRepository  stockMensuelRepository;
    @Mock private ImpayeRepository        impayeRepository;
    @Mock private ImportFichierRepository importFichierRepository;
    @Mock private ImpayeMapper            impayeMapper;

    @InjectMocks
    private ClientService clientService;

    private Client client;
    private Souscription souscription;

    @BeforeEach
    void setUp() {
        client = Client.builder()
            .id(1L)
            .numeroClient("C001")
            .nom("Alice")
            .agenceLib("Paris")
            .build();

        souscription = Souscription.builder()
            .id(10L)
            .client(client)
            .datSouscription(LocalDate.of(2024, 1, 15))
            .build();
    }

    /** Helper : retourne List<Object[]> typée pour mocker findAllAnneesMois() */
    private List<Object[]> imports(int annee, int mois) {
        return Collections.singletonList(new Object[]{annee, mois});
    }

    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Mois payé → statut PAYE dans l'historique")
    void moisPaye_statutPaye() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(souscriptionRepository.findByClientId(1L)).thenReturn(List.of(souscription));
        when(impayeRepository.findByClientIdOrderByAnneeDescMoisDesc(1L)).thenReturn(List.of());

        StockMensuel stock = StockMensuel.builder().client(client).annee(2024).mois(1).build();
        when(stockMensuelRepository.findByClientIdOrderByAnneeDescMoisDesc(1L))
            .thenReturn(List.of(stock));
        when(importFichierRepository.findAllAnneesMois()).thenReturn(imports(2024, 1));

        ClientDetailDto detail = clientService.getClientDetail(1L);

        HistoriquePaiementDto janvier = detail.getHistoriquePaiements().stream()
            .filter(h -> h.getAnnee() == 2024 && h.getMois() == 1)
            .findFirst().orElseThrow();

        assertThat(janvier.getStatut()).isEqualTo("PAYE");
    }

    @Test
    @DisplayName("Mois absent du stock mais importé → statut IMPAYE")
    void moisAbsentDuStock_statutImpaye() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(souscriptionRepository.findByClientId(1L)).thenReturn(List.of(souscription));
        when(impayeRepository.findByClientIdOrderByAnneeDescMoisDesc(1L)).thenReturn(List.of());
        when(stockMensuelRepository.findByClientIdOrderByAnneeDescMoisDesc(1L))
            .thenReturn(List.of());
        when(importFichierRepository.findAllAnneesMois()).thenReturn(imports(2024, 1));

        ClientDetailDto detail = clientService.getClientDetail(1L);

        HistoriquePaiementDto janvier = detail.getHistoriquePaiements().stream()
            .filter(h -> h.getAnnee() == 2024 && h.getMois() == 1)
            .findFirst().orElseThrow();

        assertThat(janvier.getStatut()).isEqualTo("IMPAYE");
    }

    @Test
    @DisplayName("Mois non importé → statut NON_IMPORTE")
    void moisNonImporte_statutNonImporte() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(souscriptionRepository.findByClientId(1L)).thenReturn(List.of(souscription));
        when(impayeRepository.findByClientIdOrderByAnneeDescMoisDesc(1L)).thenReturn(List.of());
        when(stockMensuelRepository.findByClientIdOrderByAnneeDescMoisDesc(1L))
            .thenReturn(List.of());
        when(importFichierRepository.findAllAnneesMois()).thenReturn(Collections.emptyList());

        ClientDetailDto detail = clientService.getClientDetail(1L);

        assertThat(detail.getHistoriquePaiements())
            .allMatch(h -> "NON_IMPORTE".equals(h.getStatut()));
    }

    @Test
    @DisplayName("Impayé régularisé en DB → statut REGULARISE dans l'historique")
    void impayeRegularise_statutRegularise() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(souscriptionRepository.findByClientId(1L)).thenReturn(List.of(souscription));

        Impaye impayeRegularise = Impaye.builder()
            .id(100L).client(client).annee(2024).mois(1)
            .statut(StatutImpaye.REGULARISE).build();
        when(impayeRepository.findByClientIdOrderByAnneeDescMoisDesc(1L))
            .thenReturn(List.of(impayeRegularise));
        when(stockMensuelRepository.findByClientIdOrderByAnneeDescMoisDesc(1L))
            .thenReturn(List.of());
        when(importFichierRepository.findAllAnneesMois()).thenReturn(imports(2024, 1));
        when(impayeMapper.toDto(impayeRegularise)).thenReturn(
            ImpayeDto.builder().id(100L).annee(2024).mois(1).statut(StatutImpaye.REGULARISE).build()
        );

        ClientDetailDto detail = clientService.getClientDetail(1L);

        HistoriquePaiementDto janvier = detail.getHistoriquePaiements().stream()
            .filter(h -> h.getAnnee() == 2024 && h.getMois() == 1)
            .findFirst().orElseThrow();

        assertThat(janvier.getStatut()).isEqualTo("REGULARISE");
    }

    @Test
    @DisplayName("Mois après sinistre, client absent → statut SINISTRE")
    void moisApresSinistre_clientAbsent_statutSinistre() {
        client.setDateSinistre(LocalDate.of(2024, 1, 1));

        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(souscriptionRepository.findByClientId(1L)).thenReturn(List.of(souscription));
        when(impayeRepository.findByClientIdOrderByAnneeDescMoisDesc(1L)).thenReturn(List.of());
        when(stockMensuelRepository.findByClientIdOrderByAnneeDescMoisDesc(1L))
            .thenReturn(List.of());
        when(importFichierRepository.findAllAnneesMois()).thenReturn(imports(2024, 1));

        ClientDetailDto detail = clientService.getClientDetail(1L);

        HistoriquePaiementDto janvier = detail.getHistoriquePaiements().stream()
            .filter(h -> h.getAnnee() == 2024 && h.getMois() == 1)
            .findFirst().orElseThrow();

        assertThat(janvier.getStatut()).isEqualTo("SINISTRE");
    }

    @Test
    @DisplayName("Mois après sinistre, client présent → statut TROP_PERCU")
    void moisApresSinistre_clientPresent_statutTropPercu() {
        client.setDateSinistre(LocalDate.of(2024, 1, 1));

        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(souscriptionRepository.findByClientId(1L)).thenReturn(List.of(souscription));
        when(impayeRepository.findByClientIdOrderByAnneeDescMoisDesc(1L)).thenReturn(List.of());

        StockMensuel stock = StockMensuel.builder().client(client).annee(2024).mois(1).build();
        when(stockMensuelRepository.findByClientIdOrderByAnneeDescMoisDesc(1L))
            .thenReturn(List.of(stock));
        when(importFichierRepository.findAllAnneesMois()).thenReturn(imports(2024, 1));

        ClientDetailDto detail = clientService.getClientDetail(1L);

        HistoriquePaiementDto janvier = detail.getHistoriquePaiements().stream()
            .filter(h -> h.getAnnee() == 2024 && h.getMois() == 1)
            .findFirst().orElseThrow();

        assertThat(janvier.getStatut()).isEqualTo("TROP_PERCU");
    }
}
