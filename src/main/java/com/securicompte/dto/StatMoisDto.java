package com.securicompte.dto;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StatMoisDto {
    private Integer annee;
    private Integer mois;
    private String moisNom;
    private long nbImpayes;
}
