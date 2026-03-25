package com.securicompte.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class ChangementPrimeDto {
    private Long clientId;
    private String numeroClient;
    private String nomClient;
    private String agenceLib;
    private String gestionnaire;
    private LocalDate dateSouscription;
    private String securicompteAvant;   // valeur dans Souscription
    private String securicompteApres;   // valeur dans StockMensuel
    private BigDecimal commissionsAvant;
    private BigDecimal commissionsApres;
}
