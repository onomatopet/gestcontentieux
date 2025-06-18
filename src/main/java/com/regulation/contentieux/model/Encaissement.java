package com.regulation.contentieux.model;

import com.regulation.contentieux.model.enums.ModeReglement;
import com.regulation.contentieux.model.enums.StatutEncaissement;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entité représentant un encaissement
 */
public class Encaissement {
    private Long id;
    private Long affaireId;
    private Double montantEncaisse;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateEncaissement;

    private ModeReglement modeReglement;
    private String reference;
    private Long banqueId;
    private StatutEncaissement statut;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    private String createdBy;
    private String updatedBy;

    // Relations (optionnelles pour éviter les chargements circulaires)
    @JsonIgnore
    private Affaire affaire;

    // Constructeurs
    public Encaissement() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.statut = StatutEncaissement.EN_ATTENTE;
        this.dateEncaissement = LocalDate.now();
    }

    public Encaissement(Long affaireId, Double montantEncaisse, ModeReglement modeReglement) {
        this();
        this.affaireId = affaireId;
        this.montantEncaisse = montantEncaisse;
        this.modeReglement = modeReglement;
    }

    // Méthodes métier

    /**
     * Vérifie si l'encaissement peut être modifié
     */
    public boolean peutEtreModifie() {
        return statut != null && statut.isModifiable();
    }

    /**
     * Vérifie si l'encaissement est comptabilisable
     */
    public boolean estComptabilisable() {
        return statut != null && statut.isComptabilisable();
    }

    /**
     * Vérifie si l'encaissement peut être annulé
     */
    public boolean peutEtreAnnule() {
        return statut != null && statut.isReversible();
    }

    /**
     * Vérifie si le mode de règlement nécessite une banque
     */
    public boolean necessiteBanque() {
        return modeReglement != null && modeReglement.isNecessiteBanque();
    }

    /**
     * Vérifie si le mode de règlement nécessite une référence
     */
    public boolean necessiteReference() {
        return modeReglement != null && modeReglement.isNecessiteReference();
    }

    /**
     * Retourne la description du mode de règlement
     */
    public String getModeReglementLibelle() {
        return modeReglement != null ? modeReglement.getLibelle() : "";
    }

    /**
     * Retourne la description du statut
     */
    public String getStatutLibelle() {
        return statut != null ? statut.getLibelle() : "";
    }

    /**
     * Vérifie si l'encaissement est validé
     */
    public boolean estValide() {
        return StatutEncaissement.VALIDE.equals(statut);
    }

    /**
     * Vérifie si l'encaissement est en attente
     */
    public boolean estEnAttente() {
        return StatutEncaissement.EN_ATTENTE.equals(statut);
    }

    /**
     * Valide l'encaissement
     */
    public void valider(String validatedBy) {
        if (!peutEtreModifie()) {
            throw new IllegalStateException("Cet encaissement ne peut plus être modifié");
        }

        this.statut = StatutEncaissement.VALIDE;
        this.updatedBy = validatedBy;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Rejette l'encaissement
     */
    public void rejeter(String rejectedBy) {
        if (!peutEtreModifie()) {
            throw new IllegalStateException("Cet encaissement ne peut plus être modifié");
        }

        this.statut = StatutEncaissement.REJETE;
        this.updatedBy = rejectedBy;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Annule l'encaissement
     */
    public void annuler(String cancelledBy) {
        if (!peutEtreAnnule()) {
            throw new IllegalStateException("Cet encaissement ne peut pas être annulé");
        }

        this.statut = StatutEncaissement.ANNULE;
        this.updatedBy = cancelledBy;
        this.updatedAt = LocalDateTime.now();
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAffaireId() { return affaireId; }
    public void setAffaireId(Long affaireId) { this.affaireId = affaireId; }

    public Double getMontantEncaisse() { return montantEncaisse; }
    public void setMontantEncaisse(Double montantEncaisse) { this.montantEncaisse = montantEncaisse; }

    public LocalDate getDateEncaissement() { return dateEncaissement; }
    public void setDateEncaissement(LocalDate dateEncaissement) { this.dateEncaissement = dateEncaissement; }

    public ModeReglement getModeReglement() { return modeReglement; }
    public void setModeReglement(ModeReglement modeReglement) { this.modeReglement = modeReglement; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public Long getBanqueId() { return banqueId; }
    public void setBanqueId(Long banqueId) { this.banqueId = banqueId; }

    public StatutEncaissement getStatut() { return statut; }
    public void setStatut(StatutEncaissement statut) {
        this.statut = statut;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }

    public Affaire getAffaire() { return affaire; }
    public void setAffaire(Affaire affaire) { this.affaire = affaire; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Encaissement that = (Encaissement) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(affaireId, that.affaireId) &&
                Objects.equals(reference, that.reference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, affaireId, reference);
    }

    @Override
    public String toString() {
        return String.format("Encaissement[%d] - Affaire %d - %s %.2f - %s",
                id, affaireId,
                modeReglement != null ? modeReglement.getLibelle() : "?",
                montantEncaisse != null ? montantEncaisse : 0.0,
                statut != null ? statut.getLibelle() : "?");
    }
}