package com.regulation.contentieux.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * DTO pour la situation générale des affaires contentieuses
 * Utilisé pour le rapport de situation générale
 */
public class SituationGeneraleDTO {

    private LocalDate dateDebut;
    private LocalDate dateFin;
    private LocalDate dateGeneration;
    private String periodeLibelle;

    // Statistiques globales
    private int totalAffaires;
    private int affairesOuvertes;
    private int affairesEnCours;
    private int affairesSoldees;
    private int affairesAnnulees;

    // Montants globaux
    private BigDecimal montantTotalAmendes;
    private BigDecimal montantTotalEncaisse;
    private BigDecimal montantRestantDu;
    private BigDecimal tauxRecouvrement;

    // Répartition par statut
    private Map<String, Integer> repartitionParStatut;
    private Map<String, BigDecimal> montantsParStatut;

    // Répartition par service
    private Map<String, Integer> affairesParService;
    private Map<String, BigDecimal> montantsParService;

    // Répartition par type de contravention
    private Map<String, Integer> affairesParTypeContravention;
    private Map<String, BigDecimal> montantsParTypeContravention;

    // Évolution mensuelle
    private List<EvolutionMensuelleDTO> evolutionMensuelle;

    // Top contrevenants
    private List<TopContrevenantDTO> topContrevenants;

    // Constructeur
    public SituationGeneraleDTO() {
        this.dateGeneration = LocalDate.now();
        this.montantTotalAmendes = BigDecimal.ZERO;
        this.montantTotalEncaisse = BigDecimal.ZERO;
        this.montantRestantDu = BigDecimal.ZERO;
        this.tauxRecouvrement = BigDecimal.ZERO;
    }

    // Getters et Setters
    public LocalDate getDateDebut() {
        return dateDebut;
    }

    public void setDateDebut(LocalDate dateDebut) {
        this.dateDebut = dateDebut;
    }

    public LocalDate getDateFin() {
        return dateFin;
    }

    public void setDateFin(LocalDate dateFin) {
        this.dateFin = dateFin;
    }

    public LocalDate getDateGeneration() {
        return dateGeneration;
    }

    public void setDateGeneration(LocalDate dateGeneration) {
        this.dateGeneration = dateGeneration;
    }

    public String getPeriodeLibelle() {
        return periodeLibelle;
    }

    public void setPeriodeLibelle(String periodeLibelle) {
        this.periodeLibelle = periodeLibelle;
    }

    public int getTotalAffaires() {
        return totalAffaires;
    }

    public void setTotalAffaires(int totalAffaires) {
        this.totalAffaires = totalAffaires;
    }

    public int getAffairesOuvertes() {
        return affairesOuvertes;
    }

    public void setAffairesOuvertes(int affairesOuvertes) {
        this.affairesOuvertes = affairesOuvertes;
    }

    public int getAffairesEnCours() {
        return affairesEnCours;
    }

    public void setAffairesEnCours(int affairesEnCours) {
        this.affairesEnCours = affairesEnCours;
    }

    public int getAffairesSoldees() {
        return affairesSoldees;
    }

    public void setAffairesSoldees(int affairesSoldees) {
        this.affairesSoldees = affairesSoldees;
    }

    public int getAffairesAnnulees() {
        return affairesAnnulees;
    }

    public void setAffairesAnnulees(int affairesAnnulees) {
        this.affairesAnnulees = affairesAnnulees;
    }

    public BigDecimal getMontantTotalAmendes() {
        return montantTotalAmendes;
    }

    public void setMontantTotalAmendes(BigDecimal montantTotalAmendes) {
        this.montantTotalAmendes = montantTotalAmendes;
    }

    public BigDecimal getMontantTotalEncaisse() {
        return montantTotalEncaisse;
    }

    public void setMontantTotalEncaisse(BigDecimal montantTotalEncaisse) {
        this.montantTotalEncaisse = montantTotalEncaisse;
    }

    public BigDecimal getMontantRestantDu() {
        return montantRestantDu;
    }

    public void setMontantRestantDu(BigDecimal montantRestantDu) {
        this.montantRestantDu = montantRestantDu;
    }

    public BigDecimal getTauxRecouvrement() {
        return tauxRecouvrement;
    }

    public void setTauxRecouvrement(BigDecimal tauxRecouvrement) {
        this.tauxRecouvrement = tauxRecouvrement;
    }

    public Map<String, Integer> getRepartitionParStatut() {
        return repartitionParStatut;
    }

    public void setRepartitionParStatut(Map<String, Integer> repartitionParStatut) {
        this.repartitionParStatut = repartitionParStatut;
    }

    public Map<String, BigDecimal> getMontantsParStatut() {
        return montantsParStatut;
    }

    public void setMontantsParStatut(Map<String, BigDecimal> montantsParStatut) {
        this.montantsParStatut = montantsParStatut;
    }

    public Map<String, Integer> getAffairesParService() {
        return affairesParService;
    }

    public void setAffairesParService(Map<String, Integer> affairesParService) {
        this.affairesParService = affairesParService;
    }

    public Map<String, BigDecimal> getMontantsParService() {
        return montantsParService;
    }

    public void setMontantsParService(Map<String, BigDecimal> montantsParService) {
        this.montantsParService = montantsParService;
    }

    public Map<String, Integer> getAffairesParTypeContravention() {
        return affairesParTypeContravention;
    }

    public void setAffairesParTypeContravention(Map<String, Integer> affairesParTypeContravention) {
        this.affairesParTypeContravention = affairesParTypeContravention;
    }

    public Map<String, BigDecimal> getMontantsParTypeContravention() {
        return montantsParTypeContravention;
    }

    public void setMontantsParTypeContravention(Map<String, BigDecimal> montantsParTypeContravention) {
        this.montantsParTypeContravention = montantsParTypeContravention;
    }

    public List<EvolutionMensuelleDTO> getEvolutionMensuelle() {
        return evolutionMensuelle;
    }

    public void setEvolutionMensuelle(List<EvolutionMensuelleDTO> evolutionMensuelle) {
        this.evolutionMensuelle = evolutionMensuelle;
    }

    public List<TopContrevenantDTO> getTopContrevenants() {
        return topContrevenants;
    }

    public void setTopContrevenants(List<TopContrevenantDTO> topContrevenants) {
        this.topContrevenants = topContrevenants;
    }

    /**
     * DTO pour l'évolution mensuelle
     */
    public static class EvolutionMensuelleDTO {
        private String mois;
        private int nombreAffaires;
        private BigDecimal montantTotal;
        private BigDecimal montantEncaisse;

        public EvolutionMensuelleDTO() {
            this.montantTotal = BigDecimal.ZERO;
            this.montantEncaisse = BigDecimal.ZERO;
        }

        // Getters et Setters
        public String getMois() {
            return mois;
        }

        public void setMois(String mois) {
            this.mois = mois;
        }

        public int getNombreAffaires() {
            return nombreAffaires;
        }

        public void setNombreAffaires(int nombreAffaires) {
            this.nombreAffaires = nombreAffaires;
        }

        public BigDecimal getMontantTotal() {
            return montantTotal;
        }

        public void setMontantTotal(BigDecimal montantTotal) {
            this.montantTotal = montantTotal;
        }

        public BigDecimal getMontantEncaisse() {
            return montantEncaisse;
        }

        public void setMontantEncaisse(BigDecimal montantEncaisse) {
            this.montantEncaisse = montantEncaisse;
        }
    }

    /**
     * DTO pour les top contrevenants
     */
    public static class TopContrevenantDTO {
        private String contrevenantNom;
        private String contrevenantCode;
        private int nombreAffaires;
        private BigDecimal montantTotal;
        private BigDecimal montantEncaisse;
        private BigDecimal montantRestant;

        public TopContrevenantDTO() {
            this.montantTotal = BigDecimal.ZERO;
            this.montantEncaisse = BigDecimal.ZERO;
            this.montantRestant = BigDecimal.ZERO;
        }

        // Getters et Setters
        public String getContrevenantNom() {
            return contrevenantNom;
        }

        public void setContrevenantNom(String contrevenantNom) {
            this.contrevenantNom = contrevenantNom;
        }

        public String getContrevenantCode() {
            return contrevenantCode;
        }

        public void setContrevenantCode(String contrevenantCode) {
            this.contrevenantCode = contrevenantCode;
        }

        public int getNombreAffaires() {
            return nombreAffaires;
        }

        public void setNombreAffaires(int nombreAffaires) {
            this.nombreAffaires = nombreAffaires;
        }

        public BigDecimal getMontantTotal() {
            return montantTotal;
        }

        public void setMontantTotal(BigDecimal montantTotal) {
            this.montantTotal = montantTotal;
        }

        public BigDecimal getMontantEncaisse() {
            return montantEncaisse;
        }

        public void setMontantEncaisse(BigDecimal montantEncaisse) {
            this.montantEncaisse = montantEncaisse;
        }

        public BigDecimal getMontantRestant() {
            return montantRestant;
        }

        public void setMontantRestant(BigDecimal montantRestant) {
            this.montantRestant = montantRestant;
        }
    }
}