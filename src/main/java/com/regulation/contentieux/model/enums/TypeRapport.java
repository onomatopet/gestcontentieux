package com.regulation.contentieux.model.enums;

/**
 * Énumération des types de rapports disponibles dans l'application
 * Correspond aux différents imprimés définis dans le cahier des charges
 */
public enum TypeRapport {

    REPARTITION_RETROCESSION("État de répartition et de rétrocession",
            "Rapport détaillant la répartition des montants entre l'État et les collectivités"),

    SITUATION_GENERALE("Situation générale des affaires",
            "Vue d'ensemble des affaires contentieuses sur une période"),

    TABLEAU_AMENDES_SERVICE("Tableau des amendes par service",
            "Répartition des amendes et contraventions par service"),

    ENCAISSEMENTS_PERIODE("État des encaissements",
            "Liste détaillée des encaissements sur une période"),

    AFFAIRES_NON_SOLDEES("Affaires non soldées",
            "Liste des affaires avec montants restant à recouvrer"),

    ETAT_MANDATEMENT("État de mandatement",
            "État pour le mandatement des parts État et collectivités"),

    CENTRE_REPARTITION("État par centre de répartition",
            "État cumulé par centre de répartition des montants"),

    INDICATEURS_REELS("État de répartition des indicateurs réels",
            "Répartition détaillée avec indicateurs de performance"),

    REPARTITION_PRODUIT("État de répartition du produit",
            "Répartition du produit des affaires contentieuses"),

    MANDATEMENT_AGENTS("État de mandatement agents",
            "État pour le mandatement des parts agents");

    private final String libelle;
    private final String description;

    TypeRapport(String libelle, String description) {
        this.libelle = libelle;
        this.description = description;
    }

    /**
     * Retourne le libellé du type de rapport
     */
    public String getLibelle() {
        return libelle;
    }

    /**
     * Retourne la description du type de rapport
     */
    public String getDescription() {
        return description;
    }

    /**
     * Vérifie si c'est un rapport de répartition
     */
    public boolean isRepartition() {
        return this == REPARTITION_RETROCESSION ||
                this == CENTRE_REPARTITION ||
                this == INDICATEURS_REELS ||
                this == REPARTITION_PRODUIT;
    }

    /**
     * Vérifie si c'est un rapport de mandatement
     */
    public boolean isMandatement() {
        return this == ETAT_MANDATEMENT || this == MANDATEMENT_AGENTS;
    }

    /**
     * Vérifie si c'est un rapport statistique
     */
    public boolean isStatistique() {
        return this == SITUATION_GENERALE ||
                this == TABLEAU_AMENDES_SERVICE ||
                this == AFFAIRES_NON_SOLDEES;
    }

    /**
     * Vérifie si c'est un rapport financier
     */
    public boolean isFinancier() {
        return this == ENCAISSEMENTS_PERIODE || isRepartition() || isMandatement();
    }

    @Override
    public String toString() {
        return libelle;
    }

    /**
     * Obtient le type de rapport à partir du libellé
     */
    public static TypeRapport fromLibelle(String libelle) {
        if (libelle == null) {
            return null;
        }

        for (TypeRapport type : values()) {
            if (type.libelle.equalsIgnoreCase(libelle)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Type de rapport inconnu: " + libelle);
    }
}