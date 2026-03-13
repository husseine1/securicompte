package com.securicompte.dto;
import com.securicompte.enums.StatutImpaye;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FiltreImpayeDto {
    private Integer annee;
    private Integer mois;
    private String agence;
    private String gestionnaire;
    private StatutImpaye statut;
    @Builder.Default private int page = 0;
    @Builder.Default private int size = 20;
}
