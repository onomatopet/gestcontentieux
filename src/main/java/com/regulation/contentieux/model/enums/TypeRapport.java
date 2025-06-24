// CORRECTION BUG - Afficher UNIQUEMENT les 8 templates prévus
// Fichier: src/main/java/com/regulation/contentieux/model/enums/TypeRapport.java
// Remplacer tout le contenu par :

package com.regulation.contentieux.model.enums;

/**
 * Énumération des 8 types de rapports officiels
 * Correspond exactement aux templates définis dans le cahier des charges
 */
public enum TypeRapport {

    // Template 1
    ETAT_REPARTITION_AFFAIRES("ETAT DE REPARTITION DES AFFAIRES CONTENTIEUSE",
            "Rapport détaillé avec les 14 colonnes de répartition complète (Template 1)"),

    // Template 2
    ETAT_MANDATEMENT("ETAT PAR SERIES DE MANDATEMENT",
            "État pour le mandatement avec colonnes parts et observations (Template 2)"),

    // Template 3
    CENTRE_REPARTITION("ETAT CUMULE PAR CENTRE DE REPARTITION",
            "État cumulé par centre avec répartition base et indicateur (Template 3)"),

    // Template 4
    INDICATEURS_REELS("ETAT DE REPARTITION DES PART DES INDICATEURS REELS",
            "Répartition détaillée par service et section avec indicateurs (Template 4)"),

    // Template 5
    REPARTITION_PRODUIT("ETAT DE REPARTITION DU PRODUIT DES AFFAIRES CONTENTIEUSES",
            "Répartition complète du produit avec 11 colonnes détaillées (Template 5)"),

    // Template 6
    ETAT_CUMULE_AGENT("ETAT CUMULE PAR AGENT",
            "Cumul des parts par agent selon leur rôle (Chef, Saisissant, DG, DD) (Template 6)"),

    // Template 7
    TABLEAU_AMENDES_SERVICE("TABLEAU DES AMENDES PAR SERVICES",
            "Répartition des amendes et contraventions par service (Template 7)"),

    // Template 8
    MANDATEMENT_AGENTS("ETAT PAR SERIES DE MANDATEMENTS",
            "État de mandatement détaillé par agent avec toutes les parts (Template 8)");

    private final String libelle;
    private final String description;

    TypeRapport(String libelle, String description) {
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
     * Retourne le numéro du template (1 à 8)
     */
    public int getNumeroTemplate() {
        switch (this) {
            case ETAT_REPARTITION_AFFAIRES: return 1;
            case ETAT_MANDATEMENT: return 2;
            case CENTRE_REPARTITION: return 3;
            case INDICATEURS_REELS: return 4;
            case REPARTITION_PRODUIT: return 5;
            case ETAT_CUMULE_AGENT: return 6;
            case TABLEAU_AMENDES_SERVICE: return 7;
            case MANDATEMENT_AGENTS: return 8;
            default: return 0;
        }
    }

    /**
     * Retourne le nom de la méthode dans RapportService pour ce type
     */
    public String getNomMethodeService() {
        switch (this) {
            case ETAT_REPARTITION_AFFAIRES: return "genererDonneesEtatRepartitionAffaires";
            case ETAT_MANDATEMENT: return "genererDonneesEtatMandatement";
            case CENTRE_REPARTITION: return "genererDonneesCentreRepartition";
            case INDICATEURS_REELS: return "genererDonneesIndicateursReels";
            case REPARTITION_PRODUIT: return "genererDonneesRepartitionProduit";
            case ETAT_CUMULE_AGENT: return "genererDonneesCumuleParAgent";
            case TABLEAU_AMENDES_SERVICE: return "genererTableauAmendesParServices";
            case MANDATEMENT_AGENTS: return "genererDonneesMandatementAgents";
            default: return "genererDonneesEtatRepartitionAffaires";
        }
    }

    @Override
    public String toString() {
        return libelle;
    }
}