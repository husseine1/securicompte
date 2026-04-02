package com.securicompte.entity;

import com.securicompte.enums.StatutImport;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "compte_ferme_import")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompteFermeImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom_fichier", nullable = false)
    private String nomFichier;

    @Column(name = "date_import")
    private LocalDateTime dateImport;

    @Column(name = "date_fin_import")
    private LocalDateTime dateFinImport;

    @Column(name = "nb_fermes")
    @Builder.Default
    private Integer nbFermes = 0;

    @Column(name = "nb_non_trouves")
    @Builder.Default
    private Integer nbNonTrouves = 0;

    @Column(name = "nb_erreurs")
    @Builder.Default
    private Integer nbErreurs = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    @Builder.Default
    private StatutImport statut = StatutImport.EN_COURS;

    @Column(name = "message_erreur", columnDefinition = "TEXT")
    private String messageErreur;

    @Column(name = "importe_par")
    private String importePar;

    @PrePersist
    protected void onCreate() {
        if (dateImport == null) dateImport = LocalDateTime.now();
    }
}
