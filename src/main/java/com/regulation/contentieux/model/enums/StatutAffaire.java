package com.regulation.contentieux.model.enums;

public enum StatutAffaire {
    OUVERTE("Ouverte", true, true, true, "#2196F3"),
    EN_COURS("En cours", true, true, true, "#FF9800"),
    CLOTUREE("Clôturée", false, true, false, "#4CAF50"),
    ANNULEE("Annulée", false, false, false, "#F44336"),
    SUSPENDUE("Suspendue", false, false, true, "#9C27B0");

    private final String libelle;
    private final boolean modifiable;
    private final boolean encaissable;
    private final boolean reportable;
    private final String couleur;

    StatutAffaire(String libelle, boolean modifiable, boolean encaissable, boolean reportable, String couleur) {
        this.libelle = libelle;
        this.modifiable = modifiable;
        this.encaissable = encaissable;
        this.reportable = reportable;
        this.couleur = couleur;
    }

    public String getLibelle() { return libelle; }
    public boolean isModifiable() { return modifiable; }
    public boolean isEncaissable() { return encaissable; }
    public boolean isReportable() { return reportable; }
    public String getCouleur() { return couleur; }

    /**
     * Retourne les transitions autorisées depuis ce statut
     */
    public StatutAffaire[] getTransitionsAutorisees() {
        return switch (this) {
            case OUVERTE -> new StatutAffaire[]{EN_COURS, SUSPENDUE, ANNULEE};
            case EN_COURS -> new StatutAffaire[]{CLOTUREE, SUSPENDUE, ANNULEE};
            case SUSPENDUE -> new StatutAffaire[]{EN_COURS, ANNULEE};
            case CLOTUREE, ANNULEE -> new StatutAffaire[]{};
        };
    }

    @Override
    public String toString() { return libelle; }
}
