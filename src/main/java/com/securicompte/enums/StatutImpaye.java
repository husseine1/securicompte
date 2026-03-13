package com.securicompte.enums;

public enum StatutImpaye {
    IMPAYE("Impayé"),
    REGULARISE("Régularisé"),
    LITIGE("En litige");

    private final String libelle;
    StatutImpaye(String libelle) { this.libelle = libelle; }
    public String getLibelle() { return libelle; }
}
