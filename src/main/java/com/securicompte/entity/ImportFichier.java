package com.securicompte.entity;

import com.securicompte.enums.StatutImport;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "import_fichier", indexes = {
    @Index(name = "idx_import_annee_mois", columnList = "annee, mois"),
    @Index(name = "idx_import_statut", columnList = "statut")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportFichier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom_fichier", nullable = false, length = 255)
    private String nomFichier;

    @Column(name = "annee", nullable = false)
    private Integer annee;

    @Column(name = "mois", nullable = false)
    private Integer mois;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 50)
    @Builder.Default
    private StatutImport statut = StatutImport.EN_COURS;

    @Column(name = "nb_nouvelles")
    @Builder.Default
    private Integer nbNouvelles = 0;

    @Column(name = "nb_anciennes")
    @Builder.Default
    private Integer nbAnciennes = 0;

    @Column(name = "nb_stock")
    @Builder.Default
    private Integer nbStock = 0;

    @Column(name = "nb_erreurs")
    @Builder.Default
    private Integer nbErreurs = 0;

    @Column(name = "message_erreur", columnDefinition = "TEXT")
    private String messageErreur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "importe_par")
    private User importePar;

    @Column(name = "date_import")
    private LocalDateTime dateImport;

    @Column(name = "date_fin_import")
    private LocalDateTime dateFinImport;

    @PrePersist
    protected void onCreate() {
        dateImport = LocalDateTime.now();
    }
}
