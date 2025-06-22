package com.regulation.contentieux.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Résultat du calcul de répartition pour un encaissement
 * ENRICHI : Inclut les parts DD et DG qui sont toujours bénéficiaires
 */
public class RepartitionResultat {

    private Long id;
    private Encaissement encaissement;

    // Montants de base
    private BigDecimal produitDisponible;
    private BigDecimal partIndicateur = BigDecimal.ZERO;
    private BigDecimal produitNet;

    // Répartition niveau 1
    private BigDecimal partFLCF;
    private BigDecimal partTresor;
    private BigDecimal produitNetAyantsDroits;

    // ENRICHISSEMENT : Parts DD et DG (toujours bénéficiaires)
    private BigDecimal partDD = BigDecimal.ZERO;
    private BigDecimal partDG = BigDecimal.ZERO;

    // Répartition niveau 2
    private BigDecimal partChefs;
    private BigDecimal partSaisissants;
    private BigDecimal partMutuelle;
    private BigDecimal partMasseCommune;
    private BigDecimal partInteressement;

    // Parts individuelles
    private List<PartIndividuelle> partsIndividuelles = new ArrayList<>();

    // Bénéficiaires génériques (pour DD/DG si agents non trouvés)
    private List<BeneficiaireGenerique> beneficiairesGeneriques = new ArrayList<>();

    // Métadonnées
    private LocalDateTime calculatedAt;
    private String calculatedBy;

    /**
     * Classe interne pour représenter une part individuelle
     */
    public static class PartIndividuelle {
        private Agent agent;
        private BigDecimal montant;
        private String role;
        private String description;

        public PartIndividuelle(Agent agent, BigDecimal montant, String role) {
            this.agent = agent;
            this.montant = montant;
            this.role = role;
        }

        // Getters et setters
        public Agent getAgent() { return agent; }
        public void setAgent(Agent agent) { this.agent = agent; }

        public BigDecimal getMontant() { return montant; }
        public void setMontant(BigDecimal montant) { this.montant = montant; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    /**
     * Classe interne pour les bénéficiaires génériques (DD/DG non trouvés)
     */
    public static class BeneficiaireGenerique {
        private String role;
        private BigDecimal montant;
        private String description;

        public BeneficiaireGenerique(String role, BigDecimal montant) {
            this.role = role;
            this.montant = montant;
            this.description = role + " (Bénéficiaire permanent)";
        }

        // Getters et setters
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public BigDecimal getMontant() { return montant; }
        public void setMontant(BigDecimal montant) { this.montant = montant; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    // Méthodes utilitaires

    /**
     * Ajoute une part individuelle
     */
    public void addPartIndividuelle(Agent agent, BigDecimal montant, String role) {
        partsIndividuelles.add(new PartIndividuelle(agent, montant, role));
    }

    /**
     * Ajoute un bénéficiaire générique (DD/DG si agent non trouvé)
     */
    public void addBeneficiaireGenerique(String role, BigDecimal montant) {
        beneficiairesGeneriques.add(new BeneficiaireGenerique(role, montant));
    }

    /**
     * Calcule le total réparti
     */
    public BigDecimal getTotalReparti() {
        BigDecimal total = BigDecimal.ZERO;

        // Parts institutionnelles
        total = total.add(partIndicateur)
                .add(partFLCF)
                .add(partTresor)
                .add(partDD)
                .add(partDG)
                .add(partMutuelle)
                .add(partMasseCommune)
                .add(partInteressement);

        // Parts individuelles
        for (PartIndividuelle part : partsIndividuelles) {
            total = total.add(part.getMontant());
        }

        // Bénéficiaires génériques
        for (BeneficiaireGenerique bg : beneficiairesGeneriques) {
            total = total.add(bg.getMontant());
        }

        return total;
    }

    /**
     * Vérifie si la répartition est équilibrée
     */
    public boolean isEquilibre() {
        BigDecimal ecart = produitDisponible.subtract(getTotalReparti()).abs();
        return ecart.compareTo(new BigDecimal("10")) <= 0; // Tolérance de 10 FCFA
    }

    // Getters et setters standards

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Encaissement getEncaissement() { return encaissement; }
    public void setEncaissement(Encaissement encaissement) { this.encaissement = encaissement; }

    public BigDecimal getProduitDisponible() { return produitDisponible; }
    public void setProduitDisponible(BigDecimal produitDisponible) { this.produitDisponible = produitDisponible; }

    public BigDecimal getPartIndicateur() { return partIndicateur; }
    public void setPartIndicateur(BigDecimal partIndicateur) { this.partIndicateur = partIndicateur; }

    public BigDecimal getProduitNet() { return produitNet; }
    public void setProduitNet(BigDecimal produitNet) { this.produitNet = produitNet; }

    public BigDecimal getPartFLCF() { return partFLCF; }
    public void setPartFLCF(BigDecimal partFLCF) { this.partFLCF = partFLCF; }

    public BigDecimal getPartTresor() { return partTresor; }
    public void setPartTresor(BigDecimal partTresor) { this.partTresor = partTresor; }

    public BigDecimal getProduitNetAyantsDroits() { return produitNetAyantsDroits; }
    public void setProduitNetAyantsDroits(BigDecimal produitNetAyantsDroits) {
        this.produitNetAyantsDroits = produitNetAyantsDroits;
    }

    public BigDecimal getPartDD() { return partDD; }
    public void setPartDD(BigDecimal partDD) { this.partDD = partDD; }

    public BigDecimal getPartDG() { return partDG; }
    public void setPartDG(BigDecimal partDG) { this.partDG = partDG; }

    public BigDecimal getPartChefs() { return partChefs; }
    public void setPartChefs(BigDecimal partChefs) { this.partChefs = partChefs; }

    public BigDecimal getPartSaisissants() { return partSaisissants; }
    public void setPartSaisissants(BigDecimal partSaisissants) { this.partSaisissants = partSaisissants; }

    public BigDecimal getPartMutuelle() { return partMutuelle; }
    public void setPartMutuelle(BigDecimal partMutuelle) { this.partMutuelle = partMutuelle; }

    public BigDecimal getPartMasseCommune() { return partMasseCommune; }
    public void setPartMasseCommune(BigDecimal partMasseCommune) { this.partMasseCommune = partMasseCommune; }

    public BigDecimal getPartInteressement() { return partInteressement; }
    public void setPartInteressement(BigDecimal partInteressement) { this.partInteressement = partInteressement; }

    public List<PartIndividuelle> getPartsIndividuelles() { return partsIndividuelles; }
    public void setPartsIndividuelles(List<PartIndividuelle> partsIndividuelles) {
        this.partsIndividuelles = partsIndividuelles;
    }

    public List<BeneficiaireGenerique> getBeneficiairesGeneriques() { return beneficiairesGeneriques; }
    public void setBeneficiairesGeneriques(List<BeneficiaireGenerique> beneficiairesGeneriques) {
        this.beneficiairesGeneriques = beneficiairesGeneriques;
    }

    public LocalDateTime getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(LocalDateTime calculatedAt) { this.calculatedAt = calculatedAt; }

    public String getCalculatedBy() { return calculatedBy; }
    public void setCalculatedBy(String calculatedBy) { this.calculatedBy = calculatedBy; }
}