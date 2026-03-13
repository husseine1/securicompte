package com.securicompte.dto;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class HistoriquePaiementDto {
    private Integer annee;
    private Integer mois;
    private String moisNom;
    private String statut;
    private boolean present;

    private static final String[] MOIS_NOMS = {
        "", "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
        "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"
    };
    public String getMoisNom() {
        if (mois != null && mois >= 1 && mois <= 12) return MOIS_NOMS[mois];
        return moisNom != null ? moisNom : "";
    }
}
