package com.securicompte.entity;

import com.securicompte.enums.StatutChangement;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "changement_client", indexes = {
    @Index(name = "idx_changement_client_periode", columnList = "annee, mois"),
    @Index(name = "idx_changement_client_statut",  columnList = "statut"),
    @Index(name = "idx_changement_client_client",  columnList = "client_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangementClient {

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

    /** Nom du champ modifié : "nom", "agenceLib", "gestionnaire", "zoneLib", "dateNaissance" */
    @Column(name = "champ", nullable = false, length = 50)
    private String champ;

    @Column(name = "valeur_avant", columnDefinition = "TEXT")
    private String valeurAvant;

    @Column(name = "valeur_apres", columnDefinition = "TEXT")
    private String valeurApres;

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
