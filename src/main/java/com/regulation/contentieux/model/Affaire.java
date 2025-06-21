package com.regulation.contentieux.model;

import com.regulation.contentieux.model.enums.StatutAffaire;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entité représentant une affaire contentieuse
 * Une affaire est liée à un contrevenant et peut avoir plusieurs contraventions et encaissements
 */
public class Affaire {

    private Long id;
    private String numeroAffaire;
    private LocalDate dateCreation;
    private LocalDate dateConstatation;
    private String lieuConstatation;
    private String description;
    private BigDecimal montantTotal;
    private BigDecimal montantEncaisse;
    private StatutAffaire statut;
    private String observations;

    // Relations
    private Contrevenant contrevenant;
    private Agent agentVerbalisateur;
    private Bureau bureau;
    private Service service;
    private List<Contravention> contraventions;
    private List<Encaissement> encaissements;

    // Métadonnées
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDate updatedAt;
    private boolean deleted;
    private String deletedBy;
    private LocalDate deletedAt;

    // Constructeurs
    public Affaire() {
        this.dateCreation = LocalDate.now();
        this.statut = StatutAffaire.OUVERTE;
        this.montantTotal = BigDecimal.ZERO;
        this.montantEncaisse = BigDecimal.ZERO;
        this.contraventions = new ArrayList<>();
        this.encaissements = new ArrayList<>();
        this.deleted = false;
        this.createdAt = LocalDateTime.now();
    }

    // Méthodes métier

    /**
     * Vérifie si l'affaire a des encaissements
     */
    public boolean hasEncaissements() {
        return encaissements != null && !encaissements.isEmpty();
    }

    /**
     * Calcule le montant total encaissé
     */
    public BigDecimal getMontantEncaisseTotal() {
        if (encaissements == null || encaissements.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return encaissements.stream()
                .filter(e -> e.getStatut() == com.regulation.contentieux.model.enums.StatutEncaissement.VALIDE)
                .map(Encaissement::getMontantEncaisse)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calcule le solde restant dû
     */
    public BigDecimal getSoldeRestant() {
        BigDecimal encaisseTotal = getMontantEncaisseTotal();
        return montantTotal.subtract(encaisseTotal);
    }

    /**
     * Vérifie si l'affaire est entièrement payée
     */
    public boolean isFullyPaid() {
        return getSoldeRestant().compareTo(BigDecimal.ZERO) <= 0;
    }

    /**
     * Ajoute une contravention à l'affaire
     */
    public void addContravention(Contravention contravention) {
        if (contraventions == null) {
            contraventions = new ArrayList<>();
        }
        contraventions.add(contravention);
        contravention.setAffaire(this);
        recalculerMontantTotal();
    }

    /**
     * Ajoute un encaissement à l'affaire
     */
    public void addEncaissement(Encaissement encaissement) {
        if (encaissements == null) {
            encaissements = new ArrayList<>();
        }
        encaissements.add(encaissement);
        encaissement.setAffaire(this);
        montantEncaisse = getMontantEncaisseTotal();
    }

    /**
     * Recalcule le montant total de l'affaire
     */
    private void recalculerMontantTotal() {
        if (contraventions != null && !contraventions.isEmpty()) {
            montantTotal = contraventions.stream()
                    .map(Contravention::getMontant)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            montantTotal = BigDecimal.ZERO;
        }
    }

    /**
     * Retourne le nom d'affichage du contrevenant
     */
    public String getContrevenantDisplayName() {
        if (contrevenant != null) {
            return contrevenant.getDisplayName();
        }
        return "Non défini";
    }

    /**
     * Vérifie si l'affaire peut être clôturée
     */
    public boolean canBeClosed() {
        return isFullyPaid() && statut != StatutAffaire.CLOSE;
    }

    // Getters et Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNumeroAffaire() {
        return numeroAffaire;
    }

    public void setNumeroAffaire(String numeroAffaire) {
        this.numeroAffaire = numeroAffaire;
    }

    public LocalDate getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDate dateCreation) {
        this.dateCreation = dateCreation;
    }

    public LocalDate getDateConstatation() {
        return dateConstatation;
    }

    public void setDateConstatation(LocalDate dateConstatation) {
        this.dateConstatation = dateConstatation;
    }

    public String getLieuConstatation() {
        return lieuConstatation;
    }

    public void setLieuConstatation(String lieuConstatation) {
        this.lieuConstatation = lieuConstatation;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public StatutAffaire getStatut() {
        return statut;
    }

    public void setStatut(StatutAffaire statut) {
        this.statut = statut;
    }

    public String getObservations() {
        return observations;
    }

    public void setObservations(String observations) {
        this.observations = observations;
    }

    public Contrevenant getContrevenant() {
        return contrevenant;
    }

    public void setContrevenant(Contrevenant contrevenant) {
        this.contrevenant = contrevenant;
    }

    public Agent getAgentVerbalisateur() {
        return agentVerbalisateur;
    }

    public void setAgentVerbalisateur(Agent agentVerbalisateur) {
        this.agentVerbalisateur = agentVerbalisateur;
    }

    public Bureau getBureau() {
        return bureau;
    }

    public void setBureau(Bureau bureau) {
        this.bureau = bureau;
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public List<Contravention> getContraventions() {
        return contraventions;
    }

    public void setContraventions(List<Contravention> contraventions) {
        this.contraventions = contraventions;
        recalculerMontantTotal();
    }

    public List<Encaissement> getEncaissements() {
        return encaissements;
    }

    public void setEncaissements(List<Encaissement> encaissements) {
        this.encaissements = encaissements;
        this.montantEncaisse = getMontantEncaisseTotal();
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDate getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDate updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
    }

    public LocalDate getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDate deletedAt) {
        this.deletedAt = deletedAt;
    }

    // Equals, HashCode et ToString

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Affaire affaire = (Affaire) o;
        return Objects.equals(id, affaire.id) &&
                Objects.equals(numeroAffaire, affaire.numeroAffaire);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, numeroAffaire);
    }

    @Override
    public String toString() {
        return "Affaire{" +
                "id=" + id +
                ", numeroAffaire='" + numeroAffaire + '\'' +
                ", dateCreation=" + dateCreation +
                ", montantTotal=" + montantTotal +
                ", statut=" + statut +
                '}';
    }
}