package com.securicompte.dto;

import com.securicompte.enums.StatutChangement;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChangementClientDto {
    private Long id;
    private Long clientId;
    private String numeroClient;
    private String nomClient;
    private String champ;
    private String champLabel;
    private String valeurAvant;
    private String valeurApres;
    private StatutChangement statut;
    private LocalDateTime dateDetection;
    private LocalDateTime dateDecision;
    private String decidePar;
    private Integer annee;
    private Integer mois;
}
