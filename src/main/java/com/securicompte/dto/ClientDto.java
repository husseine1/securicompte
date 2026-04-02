package com.securicompte.dto;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ClientDto {
    private Long id;
    private String numeroClient;
    private String nom;
    private LocalDate dateNaissance;
    private String compte;
    private String zoneLib;
    private String agenceLib;
    private String gestionnaire;
    private LocalDate dateSinistre;
    private LocalDate dateCompteFerme;
    private int nbImpayes;
    private BigDecimal montantTotalDu;
}
