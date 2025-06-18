package com.regulation.contentieux.model.enums;

public enum Devise {
    EUR("Euro", "€", "EUR", 2),
    USD("Dollar US", "$", "USD", 2),
    XAF("Franc CFA", "FCFA", "XAF", 0);

    private final String nom;
    private final String symbole;
    private final String codeISO;
    private final int decimales;

    Devise(String nom, String symbole, String codeISO, int decimales) {
        this.nom = nom;
        this.symbole = symbole;
        this.codeISO = codeISO;
        this.decimales = decimales;
    }

    public String getNom() { return nom; }
    public String getSymbole() { return symbole; }
    public String getCodeISO() { return codeISO; }
    public int getDecimales() { return decimales; }

    @Override
    public String toString() { return nom + " (" + symbole + ")"; }
}
