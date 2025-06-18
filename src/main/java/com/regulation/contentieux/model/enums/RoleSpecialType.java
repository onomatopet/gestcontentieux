package com.regulation.contentieux.model.enums;

public enum RoleSpecialType {
    DG("Directeur Général", 1, true, 1000000.0),
    DD("Directeur Départemental", 2, true, 500000.0);

    private final String libelle;
    private final int niveauHierarchique;
    private final boolean signatureAutorisee;
    private final double budgetAutorise;

    RoleSpecialType(String libelle, int niveauHierarchique, boolean signatureAutorisee, double budgetAutorise) {
        this.libelle = libelle;
        this.niveauHierarchique = niveauHierarchique;
        this.signatureAutorisee = signatureAutorisee;
        this.budgetAutorise = budgetAutorise;
    }

    public String getLibelle() { return libelle; }
    public int getNiveauHierarchique() { return niveauHierarchique; }
    public boolean isSignatureAutorisee() { return signatureAutorisee; }
    public double getBudgetAutorise() { return budgetAutorise; }

    @Override
    public String toString() { return libelle; }
}