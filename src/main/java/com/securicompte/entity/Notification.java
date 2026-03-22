package com.securicompte.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification", indexes = {
    @Index(name = "idx_notification_lu",         columnList = "lu"),
    @Index(name = "idx_notification_created_at", columnList = "created_at DESC"),
    @Index(name = "idx_notification_client",     columnList = "client_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "type", length = 50, nullable = false)
    @Builder.Default
    private String type = "CHANGEMENT_PRIME";

    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "client_nom", length = 200)
    private String clientNom;

    @Column(name = "impaye_id")
    private Long impayeId;

    @Column(name = "annee_impaye")
    private Integer anneeImpaye;

    @Column(name = "mois_impaye")
    private Integer moisImpaye;

    @Column(name = "securicompte_avant", length = 100)
    private String securicompteAvant;

    @Column(name = "securicompte_apres", length = 100)
    private String securicompteApres;

    @Column(name = "commissions_avant", precision = 15, scale = 2)
    private BigDecimal commissionsAvant;

    @Column(name = "commissions_apres", precision = 15, scale = 2)
    private BigDecimal commissionsApres;

    @Column(name = "cree_par", length = 100)
    private String creePar;

    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "lu", nullable = false)
    @Builder.Default
    private Boolean lu = false;

    @Column(name = "lu_par", length = 100)
    private String luPar;

    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    @Column(name = "date_lu")
    private LocalDateTime dateLu;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
