package com.regulation.contentieux.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entité représentant une contravention
 * Définit les infractions et leurs montants
 */
public class Contravention {

    private Long id;
    private String code;
    private String libelle;
    private String description;
    private BigDecimal montant;
    private String categorie;
    private boolean actif;

    // Relation avec l'affaire (si associée)
    private Affaire affaire;

    // Métadonnées
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;

    // Constructeurs
    public Contravention() {
        this.montant = BigDecimal.ZERO;
        this.actif = true;
        this.createdAt = LocalDateTime.now();
    }

    public Contravention(String code, String libelle, BigDecimal montant) {
        this();
        this.code = code;
        this.libelle = libelle;
        this.montant = montant;
    }

    // Méthodes métier

    /**
     * Retourne le nom d'affichage complet
     */
    public String getDisplayName() {
        return code + " - " + libelle;
    }

    /**
     * Retourne le nom d'affichage avec montant
     */
    public String getDisplayNameWithAmount() {
        return String.format("%s - %s (%.2f FCFA)", code, libelle, montant);
    }

    /**
     * Vérifie si la contravention a un montant valide
     */
    public boolean hasMontantValide() {
        return montant != null && montant.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Clone la contravention (pour association à une affaire)
     */
    public Contravention clone() {
        Contravention clone = new Contravention();
        clone.code = this.code;
        clone.libelle = this.libelle;
        clone.description = this.description;
        clone.montant = this.montant;
        clone.categorie = this.categorie;
        clone.actif = this.actif;
        return clone;
    }

    // Getters et Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getMontant() {
        return montant;
    }

    public void setMontant(BigDecimal montant) {
        this.montant = montant;
    }

    public String getCategorie() {
        return categorie;
    }

    public void setCategorie(String categorie) {
        this.categorie = categorie;
    }

    public boolean isActif() {
        return actif;
    }

    public void setActif(boolean actif) {
        this.actif = actif;
    }

    public Affaire getAffaire() {
        return affaire;
    }

    public void setAffaire(Affaire affaire) {
        this.affaire = affaire;
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

    // Equals, HashCode et ToString

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Contravention that = (Contravention) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, code);
    }

    @Override
    public String toString() {
        return "Contravention{" +
                "id=" + id +
                ", code='" + code + '\'' +
                ", libelle='" + libelle + '\'' +
                ", montant=" + montant +
                ", actif=" + actif +
                '}';
    }
}