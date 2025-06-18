package com.regulation.contentieux.model.enums;

public enum TypeContravention {
    FISCALE("Fiscale", "Direction des Impôts", 3650), // 10 ans
    DOUANIERE("Douanière", "Direction des Douanes", 1095), // 3 ans
    COMMERCIALE("Commerciale", "Direction du Commerce", 1825), // 5 ans
    ADMINISTRATIVE("Administrative", "Administration Générale", 730), // 2 ans
    ENVIRONNEMENTALE("Environnementale", "Ministère Environnement", 2190); // 6 ans

    private final String libelle;
    private final String autoriteCompetente;
    private final int delaiPrescriptionJours;

    TypeContravention(String libelle, String autoriteCompetente, int delaiPrescriptionJours) {
        this.libelle = libelle;
        this.autoriteCompetente = autoriteCompetente;
        this.delaiPrescriptionJours = delaiPrescriptionJours;
    }

    public String getLibelle() { return libelle; }
    public String getAutoriteCompetente() { return autoriteCompetente; }
    public int getDelaiPrescriptionJours() { return delaiPrescriptionJours; }

    @Override
    public String toString() { return libelle; }
}
