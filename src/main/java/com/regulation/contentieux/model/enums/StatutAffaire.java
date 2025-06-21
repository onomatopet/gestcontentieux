package com.regulation.contentieux.model.enums;

/**
 * Énumération des statuts possibles d'une affaire contentieuse
 */
public enum StatutAffaire {

    OUVERTE("Ouverte", "L'affaire est nouvellement créée"),
    EN_COURS("En cours", "L'affaire est en cours de traitement"),
    CLOSE("Clôturée", "L'affaire est clôturée"),
    ANNULEE("Annulée", "L'affaire a été annulée");

    private final String libelle;
    private final String description;

    StatutAffaire(String libelle, String description) {
        this.libelle = libelle;
        this.description = description;
    }

    /**
     * Retourne le libellé du statut
     */
    public String getLibelle() {
        return libelle;
    }

    /**
     * Retourne la description du statut
     */
    public String getDescription() {
        return description;
    }

    /**
     * Vérifie si le statut est actif (non clôturé et non annulé)
     */
    public boolean isActif() {
        return this == OUVERTE || this == EN_COURS;
    }

    /**
     * Vérifie si le statut est terminal
     */
    public boolean isTerminal() {
        return this == CLOSE || this == ANNULEE;
    }

    /**
     * Vérifie si une transition est possible vers un autre statut
     */
    public boolean canTransitionTo(StatutAffaire newStatut) {
        if (this == newStatut) {
            return false; // Pas de transition vers le même statut
        }

        switch (this) {
            case OUVERTE:
                // Depuis OUVERTE, on peut aller vers tous les autres statuts
                return true;

            case EN_COURS:
                // Depuis EN_COURS, on peut clôturer ou annuler
                return newStatut == CLOSE || newStatut == ANNULEE;

            case CLOSE:
            case ANNULEE:
                // Les statuts terminaux ne peuvent pas changer
                return false;

            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return libelle;
    }

    /**
     * Obtient le statut à partir du libellé
     */
    public static StatutAffaire fromLibelle(String libelle) {
        if (libelle == null) {
            return null;
        }

        for (StatutAffaire statut : values()) {
            if (statut.libelle.equalsIgnoreCase(libelle)) {
                return statut;
            }
        }

        throw new IllegalArgumentException("Statut d'affaire inconnu: " + libelle);
    }
}