package com.securicompte.dto;

import com.securicompte.enums.Reseau;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChangementClientDto {
    private Long id;
    private Integer annee;
    private Integer mois;
    private Reseau reseau;
    private Long clientId;
    private String numeroClient;
    private String nomClient;
    private String champ;
    private String champLabel;
    private String valeurAvant;
    private String valeurApres;
    private LocalDateTime dateDetection;
}
