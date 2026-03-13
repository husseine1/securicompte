package com.securicompte.enums;

public enum TypeSouscription {
    NOUVELLE("Nouvelle souscription"),
    ANCIENNE("Ancienne souscription");

    private final String libelle;

    TypeSouscription(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
