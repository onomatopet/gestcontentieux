package com.regulation.contentieux.model.enums;

/**
 * Énumération des statuts possibles d'un mandat
 */
public enum StatutMandat {

    BROUILLON("Brouillon", "Mandat en cours de préparation"),
    EN_ATTENTE("En attente", "Mandat créé mais non actif"),
    ACTIF("Actif", "Mandat actif - seul mandat permettant la saisie"),
    CLOTURE("Clôturé", "Mandat clôturé - consultation uniquement");

    private final String libelle;
    private final String description;

    StatutMandat(String libelle, String description) {
        this.libelle = libelle;
        this.description = description;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Vérifie si le statut permet la modification
     */
    public boolean permettreModification() {
        return this == ACTIF;
    }

    /**
     * Vérifie si le statut permet la consultation
     */
    public boolean permettreConsultation() {
        return true; // Tous les statuts permettent la consultation
    }

    @Override
    public String toString() {
        return libelle;
    }

    /**
     * Obtient le statut à partir du libellé
     */
    public static StatutMandat fromLibelle(String libelle) {
        if (libelle == null) {
            return null;
        }

        for (StatutMandat statut : values()) {
            if (statut.libelle.equalsIgnoreCase(libelle)) {
                return statut;
            }
        }

        throw new IllegalArgumentException("Statut de mandat inconnu : " + libelle);
    }
}