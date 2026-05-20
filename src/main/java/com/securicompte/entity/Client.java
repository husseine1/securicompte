package com.securicompte.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "client", indexes = {
    @Index(name = "idx_client_numero", columnList = "numero_client"),
    @Index(name = "idx_client_nom", columnList = "nom"),
    @Index(name = "idx_client_agence", columnList = "agence_lib"),
    @Index(name = "idx_client_gestionnaire", columnList = "gestionnaire")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "client_seq")
    @SequenceGenerator(name = "client_seq", sequenceName = "client_id_seq", allocationSize = 500)
    private Long id;

    @Column(name = "numero_client", nullable = false, unique = true, length = 50)
    private String numeroClient;

    @Column(name = "nom", length = 200)
    private String nom;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @Column(name = "compte", length = 100)
    private String compte;

    @Column(name = "zone_lib", length = 200)
    private String zoneLib;

    @Column(name = "agence_lib", length = 200)
    private String agenceLib;

    @Column(name = "gestionnaire", length = 200)
    private String gestionnaire;

    @Column(name = "date_sinistre")
    private LocalDate dateSinistre;

    @Column(name = "date_compte_ferme")
    private LocalDate dateCompteFerme;

    @Column(name = "actif")
    @Builder.Default
    private Boolean actif = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Relations
    @OneToMany(mappedBy = "client", cascade = {CascadeType.PERSIST, CascadeType.REMOVE}, fetch = FetchType.LAZY)
    @BatchSize(size = 1000)
    @Builder.Default
    private List<Souscription> souscriptions = new ArrayList<>();

    @OneToMany(mappedBy = "client", cascade = {CascadeType.PERSIST, CascadeType.REMOVE}, fetch = FetchType.LAZY)
    @BatchSize(size = 1000)
    @Builder.Default
    private List<StockMensuel> stocksMensuels = new ArrayList<>();

    @OneToMany(mappedBy = "client", cascade = {CascadeType.PERSIST, CascadeType.REMOVE}, fetch = FetchType.LAZY)
    @BatchSize(size = 1000)
    @Builder.Default
    private List<Impaye> impayes = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
