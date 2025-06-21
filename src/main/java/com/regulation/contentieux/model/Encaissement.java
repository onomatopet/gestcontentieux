package com.regulation.contentieux.model;

import com.regulation.contentieux.model.enums.ModeReglement;
import com.regulation.contentieux.model.enums.StatutEncaissement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entité représentant un encaissement
 * Un encaissement est lié à une affaire et représente un paiement partiel ou total
 */
public class Encaissement {

    private Long id;
    private String reference;
    private LocalDate dateEncaissement;
    private BigDecimal montantEncaisse;
    private ModeReglement modeReglement;
    private String numeroPiece;
    private String banque;
    private String observations;
    private StatutEncaissement statut;
    private Long banqueId;

    // Relations
    private Affaire affaire;
    private Agent agentValidateur;

    // Métadonnées
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private String validatedBy;
    private LocalDateTime validatedAt;

    // Constructeurs
    public Encaissement() {
        this.dateEncaissement = LocalDate.now();
        this.statut = StatutEncaissement.EN_ATTENTE;
        this.montantEncaisse = BigDecimal.ZERO;
        this.createdAt = LocalDateTime.now();
    }

    public Encaissement(String reference, BigDecimal montantEncaisse, ModeReglement modeReglement) {
        this();
        this.reference = reference;
        this.montantEncaisse = montantEncaisse;
        this.modeReglement = modeReglement;
    }

    // Méthodes métier

    /**
     * Vérifie si l'encaissement est validé
     */
    public boolean isValide() {
        return statut == StatutEncaissement.VALIDE;
    }

    /**
     * Vérifie si l'encaissement est en attente
     */
    public boolean isEnAttente() {
        return statut == StatutEncaissement.EN_ATTENTE;
    }

    /**
     * Vérifie si l'encaissement est rejeté
     */
    public boolean isRejete() {
        return statut == StatutEncaissement.REJETE;
    }

    /**
     * Retourne une description du mode de règlement avec détails
     */
    public String getDescriptionReglement() {
        StringBuilder desc = new StringBuilder(modeReglement.toString());

        if (numeroPiece != null && !numeroPiece.trim().isEmpty()) {
            desc.append(" N°").append(numeroPiece);
        }

        if (banque != null && !banque.trim().isEmpty()) {
            desc.append(" - ").append(banque);
        }

        return desc.toString();
    }

    /**
     * Vérifie si l'encaissement peut être modifié
     */
    public boolean canBeModified() {
        return statut == StatutEncaissement.EN_ATTENTE;
    }

    /**
     * Vérifie si l'encaissement peut être validé
     */
    public boolean canBeValidated() {
        return statut == StatutEncaissement.EN_ATTENTE &&
                montantEncaisse != null &&
                montantEncaisse.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Alias pour getMontantEncaisse() - compatibilité RapportService
     */

    public long getMontant() {
        return getMontantEncaisse();
    }

    public boolean peutEtreAnnule() {
        return statut == StatutEncaissement.VALIDE &&
                montant != null &&
                dateEncaissement != null;
    }

    public Long getBanqueId() {
        return banqueId;
    }

    public void setBanqueId(Long banqueId) {
        this.banqueId = banqueId;
    }

    /**
     * Retourne le libellé du mode de règlement
     */
    public String getModeReglementLibelle() {
        return modeReglement != null ? modeReglement.toString() : "";
    }

    /**
     * Retourne le libellé du statut
     */
    public String getStatutLibelle() {
        return statut != null ? statut.toString() : "";
    }

    /**
     * Retourne l'ID de l'affaire associée
     */
    public Long getAffaireId() {
        return affaire != null ? affaire.getId() : null;
    }

    /**
     * Définit l'affaire par son ID (pour compatibilité)
     */
    public void setAffaireId(Long affaireId) {
        if (affaire == null) {
            affaire = new Affaire();
        }
        affaire.setId(affaireId);
    }

    /**
     * Vérifie si l'encaissement peut être modifié
     */
    public boolean peutEtreModifie() {
        return canBeModified();
    }

    // Getters et Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public LocalDate getDateEncaissement() {
        return dateEncaissement;
    }

    public void setDateEncaissement(LocalDate dateEncaissement) {
        this.dateEncaissement = dateEncaissement;
    }

    public BigDecimal getMontantEncaisse() {
        return montantEncaisse;
    }

    public void setMontantEncaisse(BigDecimal montantEncaisse) {
        this.montantEncaisse = montantEncaisse;
    }

    public ModeReglement getModeReglement() {
        return modeReglement;
    }

    public void setModeReglement(ModeReglement modeReglement) {
        this.modeReglement = modeReglement;
    }

    public String getNumeroPiece() {
        return numeroPiece;
    }

    public void setNumeroPiece(String numeroPiece) {
        this.numeroPiece = numeroPiece;
    }

    public String getBanque() {
        return banque;
    }

    public void setBanque(String banque) {
        this.banque = banque;
    }

    public String getObservations() {
        return observations;
    }

    public void setObservations(String observations) {
        this.observations = observations;
    }

    public StatutEncaissement getStatut() {
        return statut;
    }

    public void setStatut(StatutEncaissement statut) {
        this.statut = statut;
    }

    public Affaire getAffaire() {
        return affaire;
    }

    public void setAffaire(Affaire affaire) {
        this.affaire = affaire;
    }

    public Agent getAgentValidateur() {
        return agentValidateur;
    }

    public void setAgentValidateur(Agent agentValidateur) {
        this.agentValidateur = agentValidateur;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getValidatedBy() {
        return validatedBy;
    }

    public void setValidatedBy(String validatedBy) {
        this.validatedBy = validatedBy;
    }

    public LocalDateTime getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(LocalDateTime validatedAt) {
        this.validatedAt = validatedAt;
    }

    // Equals, HashCode et ToString

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Encaissement that = (Encaissement) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(reference, that.reference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, reference);
    }

    @Override
    public String toString() {
        return "Encaissement{" +
                "id=" + id +
                ", reference='" + reference + '\'' +
                ", dateEncaissement=" + dateEncaissement +
                ", montantEncaisse=" + montantEncaisse +
                ", modeReglement=" + modeReglement +
                ", statut=" + statut +
                '}';
    }
}