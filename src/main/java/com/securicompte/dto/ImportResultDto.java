package com.securicompte.dto;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ImportResultDto {
    private Long importId;
    private String nomFichier;
    private Integer annee;
    private Integer mois;
    private String statut;
    @Builder.Default private int nbNouvelles = 0;
    @Builder.Default private int nbAnciennes = 0;
    @Builder.Default private int nbStock = 0;
    @Builder.Default private int nbImpaYesDetectes = 0;
    @Builder.Default private int nbErreurs = 0;
    private String messageErreur;
    private LocalDateTime dateImport;
    private boolean succes;
}
