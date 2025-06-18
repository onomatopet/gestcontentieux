package com.regulation.contentieux.model.enums;

/**
 * Énumération des rôles des agents sur les affaires
 */
public enum RoleSurAffaire {
    CHEF("Chef", 1.0, "Responsable principal de l'affaire"),
    SAISISSANT("Saisissant", 0.8, "Agent ayant saisi l'affaire");

    private final String libelle;
    private final double coefficient;
    private final String description;

    RoleSurAffaire(String libelle, double coefficient, String description) {
        this.libelle = libelle;
        this.coefficient = coefficient;
        this.description = description;
    }

    public String getLibelle() { return libelle; }
    public double getCoefficient() { return coefficient; }
    public String getDescription() { return description; }

    @Override
    public String toString() { return libelle; }
}