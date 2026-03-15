package com.securicompte.entity;

import com.securicompte.enums.TypeSouscription;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "souscription", indexes = {
    @Index(name = "idx_souscription_client", columnList = "client_id"),
    @Index(name = "idx_souscription_date", columnList = "dat_souscription"),
    @Index(name = "idx_souscription_type", columnList = "type_souscription")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Souscription {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "souscription_seq")
    @SequenceGenerator(name = "souscription_seq", sequenceName = "souscription_id_seq", allocationSize = 500)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "securicompte", length = 100)
    private String securicompte;

    @Column(name = "commissions", precision = 15, scale = 2)
    private BigDecimal commissions;

    @Column(name = "libel_package", length = 200)
    private String libelPackage;

    @Column(name = "option_securicompte", length = 200)
    private String optionSecuricompte;

    @Column(name = "dat_souscription", nullable = false)
    private LocalDate datSouscription;

    @Column(name = "dat_ouverture")
    private LocalDate datOuverture;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_souscription", nullable = false, length = 50)
    private TypeSouscription typeSouscription;

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
