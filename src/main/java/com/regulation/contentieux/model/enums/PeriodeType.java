package com.regulation.contentieux.model.enums;

public enum PeriodeType {
    JOUR("Jour", 1),
    SEMAINE("Semaine", 7),
    MOIS("Mois", 30),
    TRIMESTRE("Trimestre", 90),
    SEMESTRE("Semestre", 180),
    ANNEE("Année", 365),
    PERSONNALISEE("Personnalisée", 0);

    private final String libelle;
    private final int nombreJours;

    PeriodeType(String libelle, int nombreJours) {
        this.libelle = libelle;
        this.nombreJours = nombreJours;
    }

    public String getLibelle() { return libelle; }
    public int getNombreJours() { return nombreJours; }

    @Override
    public String toString() { return libelle; }
}
