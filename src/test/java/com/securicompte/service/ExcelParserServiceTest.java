package com.securicompte.service;

import com.securicompte.entity.*;
import com.securicompte.enums.TypeSouscription;
import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitaires pour ExcelParserService.
 * Couvre : extraction numéro client, parsing dates (années 2 chiffres),
 * conversion lignes Excel → entités.
 */
@DisplayName("ExcelParserService - parsing et extraction de données")
class ExcelParserServiceTest {

    private final ExcelParserService parser = new ExcelParserService();

    // ── getString ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getString() - valeur présente → retournée trimmée")
    void getString_valeurPresente_retourneeTrimmee() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("NOM", "  Alice Dupont  ");

        assertThat(parser.getString(row, "NOM")).isEqualTo("Alice Dupont");
    }

    @Test
    @DisplayName("getString() - clé absente → null")
    void getString_cleAbsente_retourneNull() {
        Map<String, Object> row = Map.of("NOM", "Alice");

        assertThat(parser.getString(row, "AGENCELIB")).isNull();
    }

    @Test
    @DisplayName("getString() - valeur vide → null (pas de chaîne vide)")
    void getString_valeurVide_retourneNull() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("NOM", "   ");

        assertThat(parser.getString(row, "NOM")).isNull();
    }

    @Test
    @DisplayName("getString() - clé insensible à la casse → valeur trouvée")
    void getString_cleInsensibleCasse_valeurTrouvee() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("NOM", "Bob");

        assertThat(parser.getString(row, "nom")).isEqualTo("Bob");
        assertThat(parser.getString(row, "Nom")).isEqualTo("Bob");
    }

    // ── getNumeroClient ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getNumeroClient() - colonne CLIENT courte (≤7) → retournée inchangée")
    void getNumeroClient_colonneClientCourte_retourneeInchangee() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("CLIENT", "C00123");

        assertThat(parser.getNumeroClient(row)).isEqualTo("C00123");
    }

    @Test
    @DisplayName("getNumeroClient() - compte bancaire (>7 chiffres) → extraction de la partie centrale")
    void getNumeroClient_compteBancaireLong_extraitPartiecentrale() {
        // Format : 0 + numeroClient(6) + clé(4) = 11 chars
        // "02117730005" → substring(1, 11-4) = substring(1, 7) = "211773"
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("CLIENT", "02117730005");

        assertThat(parser.getNumeroClient(row)).isEqualTo("211773");
    }

    @Test
    @DisplayName("getNumeroClient() - colonne COMPTE utilisée si CLIENT absent")
    void getNumeroClient_colonneCompte_utiliseeEnFallback() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("COMPTE", "02117730005");

        assertThat(parser.getNumeroClient(row)).isEqualTo("211773");
    }

    @Test
    @DisplayName("getNumeroClient() - CLIENT absent et COMPTE absent → null")
    void getNumeroClient_aucuneColonne_retourneNull() {
        Map<String, Object> row = Map.of("NOM", "Alice");

        assertThat(parser.getNumeroClient(row)).isNull();
    }

    @Test
    @DisplayName("getNumeroClient() - colonne CLIENT vide → null")
    void getNumeroClient_clientVide_retourneNull() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("CLIENT", "   ");

        assertThat(parser.getNumeroClient(row)).isNull();
    }

    // ── rowToClient ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("rowToClient() - ligne complète → client correctement mappé")
    void rowToClient_ligneComplete_clientCorrectementMappe() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("CLIENT",      "C001");
        row.put("NOM",         "Dupont Jean");
        row.put("AGENCELIB",   "Agence Paris");
        row.put("GESTIONNAIRE","Marie Curie");
        row.put("ZONELIB",     "Zone Nord");
        row.put("COMPTE",      "02117730005");

        Client client = parser.rowToClient(row);

        assertThat(client.getNumeroClient()).isEqualTo("C001");
        assertThat(client.getNom()).isEqualTo("Dupont Jean");
        assertThat(client.getAgenceLib()).isEqualTo("Agence Paris");
        assertThat(client.getGestionnaire()).isEqualTo("Marie Curie");
        assertThat(client.getZoneLib()).isEqualTo("Zone Nord");
        assertThat(client.getActif()).isTrue();
    }

    @Test
    @DisplayName("rowToClient() - champs optionnels absents → valeurs null, client actif")
    void rowToClient_champsOptionnelsAbsents_clientActif() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("CLIENT", "C002");

        Client client = parser.rowToClient(row);

        assertThat(client.getNumeroClient()).isEqualTo("C002");
        assertThat(client.getNom()).isNull();
        assertThat(client.getAgenceLib()).isNull();
        assertThat(client.getActif()).isTrue();
    }

    // ── rowToSouscription - parsing de dates ──────────────────────────────────

    @Test
    @DisplayName("rowToSouscription() - date avec année 2 chiffres (25/03/25) → 2025")
    void rowToSouscription_dateAnnee2Chiffres_corrigeeEn4Chiffres() {
        Client client = Client.builder().id(1L).numeroClient("C001").nom("Alice").build();
        ImportFichier importFichier = ImportFichier.builder()
            .id(1L).annee(2024).mois(3).build();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("CLIENT",          "C001");
        row.put("DATSOUSCRIPTION", "25/03/25");

        Souscription s = parser.rowToSouscription(row, client, TypeSouscription.NOUVELLE, importFichier);

        assertThat(s.getDatSouscription()).isEqualTo(LocalDate.of(2025, 3, 25));
    }

    @Test
    @DisplayName("rowToSouscription() - date avec année 4 chiffres → parsée correctement")
    void rowToSouscription_dateAnnee4Chiffres_parseeCorrectement() {
        Client client = Client.builder().id(1L).numeroClient("C001").nom("Alice").build();
        ImportFichier importFichier = ImportFichier.builder()
            .id(1L).annee(2024).mois(3).build();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("CLIENT",          "C001");
        row.put("DATSOUSCRIPTION", "15/06/2023");

        Souscription s = parser.rowToSouscription(row, client, TypeSouscription.ANCIENNE, importFichier);

        assertThat(s.getDatSouscription()).isEqualTo(LocalDate.of(2023, 6, 15));
    }

    @Test
    @DisplayName("rowToSouscription() - date absente → repli sur annee/mois de l'import")
    void rowToSouscription_datAbsente_repliSurImport() {
        Client client = Client.builder().id(1L).numeroClient("C001").build();
        ImportFichier importFichier = ImportFichier.builder()
            .id(1L).annee(2024).mois(5).build();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("CLIENT", "C001");
        // Pas de DATSOUSCRIPTION

        Souscription s = parser.rowToSouscription(row, client, TypeSouscription.NOUVELLE, importFichier);

        assertThat(s.getDatSouscription()).isEqualTo(LocalDate.of(2024, 5, 1));
    }

    @Test
    @DisplayName("rowToSouscription() - type souscription bien assigné")
    void rowToSouscription_typeCorrectementAssigne() {
        Client client = Client.builder().id(1L).build();
        ImportFichier importFichier = ImportFichier.builder().id(1L).annee(2024).mois(1).build();
        Map<String, Object> row = new LinkedHashMap<>();

        Souscription nouvelle = parser.rowToSouscription(row, client, TypeSouscription.NOUVELLE, importFichier);
        Souscription ancienne = parser.rowToSouscription(row, client, TypeSouscription.ANCIENNE, importFichier);

        assertThat(nouvelle.getTypeSouscription()).isEqualTo(TypeSouscription.NOUVELLE);
        assertThat(ancienne.getTypeSouscription()).isEqualTo(TypeSouscription.ANCIENNE);
    }

    // ── rowToStock ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("rowToStock() - annee/mois et client correctement assignés")
    void rowToStock_anneeEtMoisCorrectementAssignes() {
        Client client = Client.builder().id(1L).numeroClient("C001").build();
        ImportFichier importFichier = ImportFichier.builder().id(1L).annee(2024).mois(7).build();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("CLIENT",       "C001");
        row.put("SECURICOMPTE", "SC-BASE");
        row.put("AGENCELIB",    "Paris");

        StockMensuel stock = parser.rowToStock(row, client, 2024, 7, importFichier);

        assertThat(stock.getAnnee()).isEqualTo(2024);
        assertThat(stock.getMois()).isEqualTo(7);
        assertThat(stock.getClient()).isEqualTo(client);
        assertThat(stock.getSecuricompte()).isEqualTo("SC-BASE");
        assertThat(stock.getAgenceLib()).isEqualTo("Paris");
    }
}
