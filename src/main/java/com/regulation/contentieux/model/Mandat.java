package com.regulation.contentieux.model;

import com.regulation.contentieux.model.enums.StatutMandat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entité représentant un mandat de gestion
 * Un mandat correspond à une période mensuelle de gestion des affaires
 * Format numéro : YYMM0001 (ex: 250600001)
 */
public class Mandat {

    private Long id;
    private String numeroMandat;
    private String description;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private StatutMandat statut;
    private boolean actif;
    private LocalDateTime dateCloture;

    // Statistiques (champs calculés)
    private int nombreAffaires;
    private int nombreEncaissements;
    private BigDecimal montantTotal;

    // Métadonnées
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    // Constructeurs
    public Mandat() {
        this.statut = StatutMandat.BROUILLON;
        this.actif = false;
        this.createdAt = LocalDateTime.now();
    }

    public Mandat(String numeroMandat, LocalDate dateDebut, LocalDate dateFin) {
        this();
        this.numeroMandat = numeroMandat;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
    }

    // Méthodes métier

    /**
     * Vérifie si le mandat peut être activé
     */
    public boolean peutEtreActive() {
        return statut == StatutMandat.BROUILLON || statut == StatutMandat.EN_ATTENTE;
    }

    /**
     * Vérifie si le mandat peut être clôturé
     */
    public boolean peutEtreCloture() {
        return statut == StatutMandat.ACTIF && nombreAffaires > 0;
    }

    /**
     * Vérifie si une date est dans la période du mandat
     */
    public boolean contientDate(LocalDate date) {
        if (date == null || dateDebut == null || dateFin == null) {
            return false;
        }
        return !date.isBefore(dateDebut) && !date.isAfter(dateFin);
    }

    /**
     * Retourne la période formatée du mandat
     */
    public String getPeriodeFormatee() {
        if (dateDebut != null && dateFin != null) {
            return String.format("%s au %s",
                    dateDebut.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    dateFin.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        }
        return "";
    }

    /**
     * Retourne le mois/année du mandat
     */
    public String getMoisAnnee() {
        if (dateDebut != null) {
            return dateDebut.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        }
        return "";
    }

    // Getters et Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNumeroMandat() {
        return numeroMandat;
    }

    public void setNumeroMandat(String numeroMandat) {
        this.numeroMandat = numeroMandat;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

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

    public StatutMandat getStatut() {
        return statut;
    }

    public void setStatut(StatutMandat statut) {
        this.statut = statut;
    }

    public boolean isActif() {
        return actif;
    }

    public void setActif(boolean actif) {
        this.actif = actif;
    }

    public LocalDateTime getDateCloture() {
        return dateCloture;
    }

    public void setDateCloture(LocalDateTime dateCloture) {
        this.dateCloture = dateCloture;
    }

    public int getNombreAffaires() {
        return nombreAffaires;
    }

    public void setNombreAffaires(int nombreAffaires) {
        this.nombreAffaires = nombreAffaires;
    }

    public int getNombreEncaissements() {
        return nombreEncaissements;
    }

    public void setNombreEncaissements(int nombreEncaissements) {
        this.nombreEncaissements = nombreEncaissements;
    }

    public BigDecimal getMontantTotal() {
        return montantTotal;
    }

    public void setMontantTotal(BigDecimal montantTotal) {
        this.montantTotal = montantTotal;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    // Equals, HashCode et ToString

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Mandat mandat = (Mandat) o;
        return Objects.equals(id, mandat.id) &&
                Objects.equals(numeroMandat, mandat.numeroMandat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, numeroMandat);
    }

    @Override
    public String toString() {
        return "Mandat{" +
                "id=" + id +
                ", numeroMandat='" + numeroMandat + '\'' +
                ", statut=" + statut +
                ", actif=" + actif +
                ", periode='" + getPeriodeFormatee() + '\'' +
                '}';
    }
}