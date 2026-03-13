package com.securicompte.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_mensuel", indexes = {
    @Index(name = "idx_stock_client", columnList = "client_id"),
    @Index(name = "idx_stock_annee_mois", columnList = "annee, mois"),
    @Index(name = "idx_stock_agence", columnList = "agence_lib"),
    @Index(name = "idx_stock_gestionnaire", columnList = "gestionnaire")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMensuel {

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

    @Column(name = "securicompte", length = 100)
    private String securicompte;

    @Column(name = "commissions", precision = 15, scale = 2)
    private BigDecimal commissions;

    @Column(name = "libel_package", length = 200)
    private String libelPackage;

    @Column(name = "option_securicompte", length = 200)
    private String optionSecuricompte;

    @Column(name = "dat_souscription")
    private LocalDate datSouscription;

    @Column(name = "zone_lib", length = 200)
    private String zoneLib;

    @Column(name = "agence_lib", length = 200)
    private String agenceLib;

    @Column(name = "gestionnaire", length = 200)
    private String gestionnaire;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_fichier_id")
    private ImportFichier importFichier;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
