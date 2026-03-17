package com.securicompte.dto;
import com.securicompte.enums.StatutImpaye;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ImpayeDto {
    private Long id;
    private Long clientId;
    private String numeroClient;
    private String nomClient;
    private String agenceLib;
    private String gestionnaire;
    private String zoneLib;
    private Integer annee;
    private Integer mois;
    private String moisNom;
    private StatutImpaye statut;
    private BigDecimal montantDu;
    private String securicompte;
    private LocalDateTime dateDetection;
    private LocalDateTime dateRegularisation;
    private String regularisePar;
    private String commentaire;
}
