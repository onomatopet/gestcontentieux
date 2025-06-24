package com.regulation.contentieux.model.enums;

/**
 * Énumération des types de rapports disponibles dans l'application
 * Correspond aux différents imprimés définis dans le cahier des charges
 * ENRICHI avec les 8 templates de rapports
 */
public enum TypeRapport {

    // Template 1 : État de répartition des affaires contentieuses
    ETAT_REPARTITION_AFFAIRES("État de répartition des affaires contentieuses",
            "Rapport détaillé avec les 14 colonnes de répartition complète (Template 1)"),

    // Rapport de synthèse (ancien REPARTITION_RETROCESSION)
    REPARTITION_RETROCESSION("État de répartition et de rétrocession",
            "Rapport de synthèse de la répartition État/Collectivités (60/40)"),

    SITUATION_GENERALE("Situation générale des affaires",
            "Vue d'ensemble des affaires contentieuses sur une période"),

    // Template 7 : Tableau des amendes par service
    TABLEAU_AMENDES_SERVICE("Tableau des amendes par service",
            "Répartition des amendes et contraventions par service (Template 7)"),

    ENCAISSEMENTS_PERIODE("État des encaissements",
            "Liste détaillée des encaissements sur une période"),

    AFFAIRES_NON_SOLDEES("Affaires non soldées",
            "Liste des affaires avec montants restant à recouvrer"),

    // Template 2 : État par séries de mandatement
    ETAT_MANDATEMENT("État par séries de mandatement",
            "État pour le mandatement avec colonnes parts et observations (Template 2)"),

    // Template 3 : État cumulé par centre de répartition
    CENTRE_REPARTITION("État cumulé par centre de répartition",
            "État cumulé par centre avec répartition base et indicateur (Template 3)"),

    // Template 4 : État de répartition des indicateurs réels
    INDICATEURS_REELS("État de répartition des parts des indicateurs réels",
            "Répartition détaillée par service et section avec indicateurs (Template 4)"),

    // Template 5 : État de répartition du produit
    REPARTITION_PRODUIT("État de répartition du produit des affaires contentieuses",
            "Répartition complète du produit avec 11 colonnes détaillées (Template 5)"),

    // Template 6 : État cumulé par agent (NOUVEAU)
    ETAT_CUMULE_AGENT("État cumulé par agent",
            "Cumul des parts par agent selon leur rôle (Chef, Saisissant, DG, DD) (Template 6)"),

    // Template 8 : État par séries de mandatements (agents)
    MANDATEMENT_AGENTS("État par séries de mandatements",
            "État de mandatement détaillé par agent avec toutes les parts (Template 8)");

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
        return this == ETAT_REPARTITION_AFFAIRES ||  // Template 1
                this == REPARTITION_RETROCESSION ||
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
                this == AFFAIRES_NON_SOLDEES ||
                this == ETAT_CUMULE_AGENT;  // Ajout du cumul par agent
    }

    /**
     * Vérifie si c'est un rapport financier
     */
    public boolean isFinancier() {
        return this == ENCAISSEMENTS_PERIODE || isRepartition() || isMandatement();
    }

    /**
     * Vérifie si c'est un des 8 templates officiels
     */
    public boolean isTemplateOfficiel() {
        return this == ETAT_REPARTITION_AFFAIRES ||    // Template 1
                this == ETAT_MANDATEMENT ||             // Template 2
                this == CENTRE_REPARTITION ||           // Template 3
                this == INDICATEURS_REELS ||            // Template 4
                this == REPARTITION_PRODUIT ||          // Template 5
                this == ETAT_CUMULE_AGENT ||            // Template 6
                this == TABLEAU_AMENDES_SERVICE ||      // Template 7
                this == MANDATEMENT_AGENTS;             // Template 8
    }

    /**
     * Retourne le numéro du template (1 à 8) ou 0 si ce n'est pas un template
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
            case ETAT_REPARTITION_AFFAIRES: return "genererDonneesEtatRepartitionAffaires"; // CORRIGÉ
            case REPARTITION_RETROCESSION: return "genererRapportRepartition";
            case SITUATION_GENERALE: return "genererSituationGenerale";
            case TABLEAU_AMENDES_SERVICE: return "genererTableauAmendesParServices";
            case ENCAISSEMENTS_PERIODE: return "genererRapportEncaissements";
            case AFFAIRES_NON_SOLDEES: return "genererRapportAffairesNonSoldees";
            case ETAT_MANDATEMENT: return "genererDonneesEtatMandatement";
            case CENTRE_REPARTITION: return "genererDonneesCentreRepartition";
            case INDICATEURS_REELS: return "genererDonneesIndicateursReels";
            case REPARTITION_PRODUIT: return "genererDonneesRepartitionProduit";
            case ETAT_CUMULE_AGENT: return "genererDonneesCumuleParAgent";
            case MANDATEMENT_AGENTS: return "genererDonneesMandatementAgents";
            default: return "genererRapportRepartition"; // Par défaut
        }
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

    /**
     * Obtient le type de rapport correspondant à un numéro de template
     */
    public static TypeRapport fromTemplate(int numeroTemplate) {
        for (TypeRapport type : values()) {
            if (type.getNumeroTemplate() == numeroTemplate) {
                return type;
            }
        }
        throw new IllegalArgumentException("Numéro de template invalide: " + numeroTemplate);
    }
}