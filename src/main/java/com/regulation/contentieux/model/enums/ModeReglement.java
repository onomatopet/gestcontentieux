package com.regulation.contentieux.model.enums;

public enum ModeReglement {
    ESPECES("Espèces", false, false, 0),
    CHEQUE("Chèque", true, true, 2),
    VIREMENT("Virement", true, true, 1),
    MANDAT("Mandat", false, true, 3),
    CARTE_BANCAIRE("Carte bancaire", true, false, 0);

    private final String libelle;
    private final boolean necessiteBanque;
    private final boolean necessiteReference;
    private final int delaiEncaissement;

    ModeReglement(String libelle, boolean necessiteBanque, boolean necessiteReference, int delaiEncaissement) {
        this.libelle = libelle;
        this.necessiteBanque = necessiteBanque;
        this.necessiteReference = necessiteReference;
        this.delaiEncaissement = delaiEncaissement;
    }

    public String getLibelle() { return libelle; }
    public boolean isNecessiteBanque() { return necessiteBanque; }
    public boolean isNecessiteReference() { return necessiteReference; }
    public int getDelaiEncaissement() { return delaiEncaissement; }

    @Override
    public String toString() { return libelle; }
}
