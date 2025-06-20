package com.regulation.contentieux.model;

import com.regulation.contentieux.dao.ContraventionDAO;
import com.regulation.contentieux.model.enums.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.regulation.contentieux.model.Contravention;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Entité représentant une affaire contentieuse
 */
public class Affaire {
    private Long id;
    private String numeroAffaire;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateCreation;

    private Double montantAmendeTotal;
    private StatutAffaire statut;
    private Long contrevenantId;
    private Long contraventionId;
    private Long bureauId;
    private Long serviceId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    private String createdBy;
    private String updatedBy;

    // Relations (supprimées temporairement pour éviter les erreurs)
    @JsonIgnore
    private Contrevenant contrevenant;

    @JsonIgnore
    private List<AffaireActeur> acteurs = new ArrayList<>();

    /**
     * Alias pour getMontantAmendeTotal() - REQUIS PAR RapportService
     */
    public Double getMontantAmende() {
        return getMontantAmendeTotal();
    }

    /**
     * Retourne l'objet Contravention associé à cette affaire
     * CORRECTION POUR RapportService
     */
    public Contravention getTypeContravention() {
        return getContravention();
    }

    /**
     * Retourne l'objet Contravention pour RapportService
     */
    public Contravention getContravention() {
        if (contraventionId == null) {
            return null;
        }

        // Charge la contravention depuis la base de données
        ContraventionDAO contraventionDAO = new ContraventionDAO();
        return contraventionDAO.findById(contraventionId).orElse(null);
    }

    // Constructeurs
    public Affaire() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.statut = StatutAffaire.OUVERTE;
    }

    public Affaire(String numeroAffaire, LocalDate dateCreation, Double montantAmendeTotal) {
        this();
        this.numeroAffaire = numeroAffaire;
        this.dateCreation = dateCreation;
        this.montantAmendeTotal = montantAmendeTotal;
    }

    // Méthodes métier simplifiées
    public boolean peutEtreModifiee() {
        return statut != null && statut.isModifiable();
    }

    public boolean peutRecevoirEncaissement() {
        return statut != null && statut.isEncaissable();
    }

    public Double getSoldeRestant() {
        return montantAmendeTotal; // Simplifié pour l'instant
    }

    public boolean estSoldee() {
        return getSoldeRestant() <= 0;
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNumeroAffaire() { return numeroAffaire; }
    public void setNumeroAffaire(String numeroAffaire) { this.numeroAffaire = numeroAffaire; }

    public LocalDate getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDate dateCreation) { this.dateCreation = dateCreation; }

    public Double getMontantAmendeTotal() { return montantAmendeTotal; }
    public void setMontantAmendeTotal(Double montantAmendeTotal) { this.montantAmendeTotal = montantAmendeTotal; }

    public StatutAffaire getStatut() { return statut; }
    public void setStatut(StatutAffaire statut) {
        this.statut = statut;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getContrevenantId() { return contrevenantId; }
    public void setContrevenantId(Long contrevenantId) { this.contrevenantId = contrevenantId; }

    public Long getContraventionId() { return contraventionId; }
    public void setContraventionId(Long contraventionId) { this.contraventionId = contraventionId; }

    public Long getBureauId() { return bureauId; }
    public void setBureauId(Long bureauId) { this.bureauId = bureauId; }

    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long serviceId) { this.serviceId = serviceId; }

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

    // Relations
    public Contrevenant getContrevenant() { return contrevenant; }
    public void setContrevenant(Contrevenant contrevenant) { this.contrevenant = contrevenant; }

    public List<AffaireActeur> getActeurs() { return acteurs; }
    public void setActeurs(List<AffaireActeur> acteurs) { this.acteurs = acteurs; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Affaire affaire = (Affaire) o;
        return Objects.equals(id, affaire.id) && Objects.equals(numeroAffaire, affaire.numeroAffaire);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, numeroAffaire);
    }

    @Override
    public String toString() {
        return numeroAffaire + " - " + (contrevenant != null ? contrevenant.getNomComplet() : "");
    }
}