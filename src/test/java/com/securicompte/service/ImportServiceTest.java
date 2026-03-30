package com.securicompte.service;

import com.securicompte.dto.ChangementPrimeDto;
import com.securicompte.entity.*;
import com.securicompte.enums.StatutImport;
import com.securicompte.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImportService - gestion des imports et changements de prime")
class ImportServiceTest {

    @Mock private ExcelParserService      excelParserService;
    @Mock private ImpayeDetectionService  impayeDetectionService;
    @Mock private NotificationService     notificationService;
    @Mock private ImportFichierRepository importFichierRepository;
    @Mock private ClientRepository        clientRepository;
    @Mock private SouscriptionRepository  souscriptionRepository;
    @Mock private StockMensuelRepository  stockMensuelRepository;
    @Mock private ImpayeRepository        impayeRepository;
    @Mock private PlatformTransactionManager transactionManager;

    @InjectMocks
    private ImportService importService;

    // ── getImportById ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getImportById() - import existant → retourne l'entité")
    void getImportById_existant_retourneImport() {
        ImportFichier imp = ImportFichier.builder().id(1L).annee(2024).mois(3).build();
        when(importFichierRepository.findById(1L)).thenReturn(Optional.of(imp));

        ImportFichier result = importService.getImportById(1L);

        assertThat(result).isEqualTo(imp);
    }

    @Test
    @DisplayName("getImportById() - import inexistant → IllegalArgumentException avec message")
    void getImportById_inexistant_throwsIllegalArgument() {
        when(importFichierRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> importService.getImportById(999L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("999");
    }

    // ── countImportsEnCours ───────────────────────────────────────────────────

    @Test
    @DisplayName("countImportsEnCours() - délègue au repository")
    void countImportsEnCours_delegueAuRepository() {
        when(importFichierRepository.countByStatut(StatutImport.EN_COURS)).thenReturn(2L);

        long count = importService.countImportsEnCours();

        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("countImportsEnCours() - aucun en cours → retourne 0")
    void countImportsEnCours_aucunEnCours_retourneZero() {
        when(importFichierRepository.countByStatut(StatutImport.EN_COURS)).thenReturn(0L);

        assertThat(importService.countImportsEnCours()).isEqualTo(0L);
    }

    // ── getTousLesImports ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getTousLesImports() - retourne tous les imports triés")
    void getTousLesImports_retourneListeComplete() {
        ImportFichier imp1 = ImportFichier.builder().id(1L).annee(2024).mois(3).build();
        ImportFichier imp2 = ImportFichier.builder().id(2L).annee(2024).mois(2).build();
        when(importFichierRepository.findAllByOrderByAnneeDescMoisDesc())
            .thenReturn(List.of(imp1, imp2));

        List<ImportFichier> result = importService.getTousLesImports();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    // ── getChangementsPrime ───────────────────────────────────────────────────

    @Test
    @DisplayName("getChangementsPrime() - aucun stock pour le mois → liste vide immédiatement")
    void getChangementsPrime_stockVide_retourneListeVide() {
        when(stockMensuelRepository.findByAnneeAndMoisWithClient(2024, 3)).thenReturn(List.of());

        List<ChangementPrimeDto> result = importService.getChangementsPrime(2024, 3);

        assertThat(result).isEmpty();
        verifyNoInteractions(souscriptionRepository);
    }

    @Test
    @DisplayName("getChangementsPrime() - primes identiques → aucun changement retourné")
    void getChangementsPrime_primesIdentiques_listeVide() {
        Client client = Client.builder().id(1L).numeroClient("C001").nom("Alice").build();
        StockMensuel stock = StockMensuel.builder()
            .client(client).annee(2024).mois(3).securicompte("SC-X").build();

        Souscription souscription = Souscription.builder()
            .client(client).securicompte("SC-X").build();

        when(stockMensuelRepository.findByAnneeAndMoisWithClient(2024, 3))
            .thenReturn(List.of(stock));
        when(souscriptionRepository.findAllByClientIdsOrderByDateDesc(List.of(1L)))
            .thenReturn(List.of(souscription));

        List<ChangementPrimeDto> result = importService.getChangementsPrime(2024, 3);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getChangementsPrime() - prime modifiée → changement détecté dans le résultat")
    void getChangementsPrime_primeModifiee_changementDetecte() {
        Client client = Client.builder()
            .id(1L).numeroClient("C001").nom("Alice").agenceLib("Paris").build();

        StockMensuel stock = StockMensuel.builder()
            .client(client).annee(2024).mois(3)
            .securicompte("SC-NOUVEAU").build();

        Souscription souscription = Souscription.builder()
            .client(client).securicompte("SC-ANCIEN")
            .datSouscription(java.time.LocalDate.of(2023, 6, 1))
            .build();

        when(stockMensuelRepository.findByAnneeAndMoisWithClient(2024, 3))
            .thenReturn(List.of(stock));
        when(souscriptionRepository.findAllByClientIdsOrderByDateDesc(List.of(1L)))
            .thenReturn(List.of(souscription));

        List<ChangementPrimeDto> result = importService.getChangementsPrime(2024, 3);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNumeroClient()).isEqualTo("C001");
        assertThat(result.get(0).getSecuricompteAvant()).isEqualTo("SC-ANCIEN");
        assertThat(result.get(0).getSecuricompteApres()).isEqualTo("SC-NOUVEAU");
    }

    // ── getImportsByAnnee ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getImportsByAnnee() - retourne uniquement les imports de l'année")
    void getImportsByAnnee_retourneImportsDeLAnnee() {
        ImportFichier imp = ImportFichier.builder().id(1L).annee(2024).mois(5).build();
        when(importFichierRepository.findByAnneeOrderByMoisDesc(2024)).thenReturn(List.of(imp));

        List<ImportFichier> result = importService.getImportsByAnnee(2024);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAnnee()).isEqualTo(2024);
    }
}
