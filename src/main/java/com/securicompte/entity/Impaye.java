package com.securicompte.entity;

import com.securicompte.enums.StatutImpaye;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "impaye", indexes = {
    @Index(name = "idx_impaye_client", columnList = "client_id"),
    @Index(name = "idx_impaye_annee_mois", columnList = "annee, mois"),
    @Index(name = "idx_impaye_statut", columnList = "statut"),
    @Index(name = "idx_impaye_agence", columnList = "agence_lib"),
    @Index(name = "idx_impaye_gestionnaire", columnList = "gestionnaire")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Impaye {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "impaye_seq")
    @SequenceGenerator(name = "impaye_seq", sequenceName = "impaye_id_seq", allocationSize = 500)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "souscription_id")
    private Souscription souscription;

    @Column(name = "annee", nullable = false)
    private Integer annee;

    @Column(name = "mois", nullable = false)
    private Integer mois;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    @Builder.Default
    private StatutImpaye statut = StatutImpaye.IMPAYE;

    @Column(name = "montant_du", precision = 15, scale = 2)
    private BigDecimal montantDu;

    @Column(name = "date_detection")
    private LocalDateTime dateDetection;

    @Column(name = "date_regularisation")
    private LocalDateTime dateRegularisation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "regularise_par")
    private User regularisePar;

    @Column(name = "commentaire", columnDefinition = "TEXT")
    private String commentaire;

    @Column(name = "agence_lib", length = 200)
    private String agenceLib;

    @Column(name = "gestionnaire", length = 200)
    private String gestionnaire;

    @Column(name = "zone_lib", length = 200)
    private String zoneLib;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (dateDetection == null) {
            dateDetection = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getMoisNom() {
        String[] moisNoms = {"", "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
                             "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"};
        if (mois != null && mois >= 1 && mois <= 12) {
            return moisNoms[mois];
        }
        return "";
    }
}
