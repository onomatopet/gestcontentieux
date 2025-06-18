package com.regulation.contentieux.model.enums;

public enum StatutEncaissement {
    EN_ATTENTE("En attente", true, false, true, "#FF9800"),
    VALIDE("Validé", false, true, false, "#4CAF50"),
    REJETE("Rejeté", true, false, true, "#F44336"),
    ANNULE("Annulé", false, false, false, "#9E9E9E"),
    REMBOURSE("Remboursé", false, false, false, "#9C27B0");

    private final String libelle;
    private final boolean modifiable;
    private final boolean comptabilisable;
    private final boolean reversible;
    private final String couleur;

    StatutEncaissement(String libelle, boolean modifiable, boolean comptabilisable, boolean reversible, String couleur) {
        this.libelle = libelle;
        this.modifiable = modifiable;
        this.comptabilisable = comptabilisable;
        this.reversible = reversible;
        this.couleur = couleur;
    }

    public String getLibelle() { return libelle; }
    public boolean isModifiable() { return modifiable; }
    public boolean isComptabilisable() { return comptabilisable; }
    public boolean isReversible() { return reversible; }
    public String getCouleur() { return couleur; }

    @Override
    public String toString() { return libelle; }
}
