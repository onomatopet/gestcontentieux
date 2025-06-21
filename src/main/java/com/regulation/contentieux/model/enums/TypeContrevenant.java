package com.regulation.contentieux.model.enums;

/**
 * Énumération des types de contrevenants
 * Distingue les personnes physiques des personnes morales
 */
public enum TypeContrevenant {

    PERSONNE_PHYSIQUE("Personne physique"),
    PERSONNE_MORALE("Personne morale");

    private final String libelle;

    TypeContrevenant(String libelle) {
        this.libelle = libelle;
    }

    /**
     * Retourne le libellé du type
     */
    public String getLibelle() {
        return libelle;
    }

    /**
     * Vérifie si c'est une personne physique
     */
    public boolean isPersonnePhysique() {
        return this == PERSONNE_PHYSIQUE;
    }

    /**
     * Vérifie si c'est une personne morale
     */
    public boolean isPersonneMorale() {
        return this == PERSONNE_MORALE;
    }

    @Override
    public String toString() {
        return libelle;
    }

    /**
     * Obtient le type à partir du libellé
     */
    public static TypeContrevenant fromLibelle(String libelle) {
        if (libelle == null) {
            return null;
        }

        for (TypeContrevenant type : values()) {
            if (type.libelle.equalsIgnoreCase(libelle)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Type de contrevenant inconnu: " + libelle);
    }
}