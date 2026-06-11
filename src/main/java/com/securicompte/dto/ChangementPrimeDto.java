package com.securicompte.dto;

import com.securicompte.enums.Reseau;
import com.securicompte.enums.StatutChangement;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class ChangementPrimeDto {
    private Long id;
    private Integer annee;
    private Integer mois;
    private Reseau reseau;
    private Long clientId;
    private String numeroClient;
    private String nomClient;
    private String agenceLib;
    private String gestionnaire;
    private LocalDate dateSouscription;
    private String securicompteAvant;
    private String securicompteApres;
    private BigDecimal commissionsAvant;
    private BigDecimal commissionsApres;
    private StatutChangement statut;
    private LocalDateTime dateDetection;
    private LocalDateTime dateDecision;
    private String decidePar;
}
