package com.securicompte.entity;

import com.securicompte.enums.StatutChangement;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "changement_prime", indexes = {
    @Index(name = "idx_changement_prime_statut",  columnList = "statut"),
    @Index(name = "idx_changement_prime_periode", columnList = "annee, mois"),
    @Index(name = "idx_changement_prime_client",  columnList = "client_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangementPrime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "annee", nullable = false)
    private Integer annee;

    @Column(name = "mois", nullable = false)
    private Integer mois;

    @Column(name = "securicompte_avant", length = 100)
    private String securicompteAvant;

    @Column(name = "securicompte_apres", length = 100)
    private String securicompteApres;

    @Column(name = "commissions_avant")
    private BigDecimal commissionsAvant;

    @Column(name = "commissions_apres")
    private BigDecimal commissionsApres;

    @Column(name = "dat_souscription")
    private LocalDate datSouscription;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    @Builder.Default
    private StatutChangement statut = StatutChangement.EN_ATTENTE;

    @Column(name = "date_detection", nullable = false)
    private LocalDateTime dateDetection;

    @Column(name = "date_decision")
    private LocalDateTime dateDecision;

    @Column(name = "decide_par", length = 100)
    private String decidePar;
}
