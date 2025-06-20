package com.regulation.contentieux.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entité représentant un centre
 * HARMONISÉE AVEC ReferentielController
 */
public class Centre {
    private Long id;
    private String codeCentre;
    private String nomCentre;
    private String description;
    private boolean actif = true;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // Constructeurs
    public Centre() {
        this.createdAt = LocalDateTime.now();
        this.actif = true;
    }

    public Centre(String codeCentre, String nomCentre) {
        this();
        this.codeCentre = codeCentre;
        this.nomCentre = nomCentre;
    }

    // ===== MÉTHODES REQUISES PAR ReferentielController =====

    /**
     * Méthode unifiée pour getCode() - REQUIS PAR ReferentielController
     */
    public String getCode() {
        return codeCentre;
    }

    /**
     * Méthode unifiée pour setCode() - REQUIS PAR ReferentielController
     */
    public void setCode(String code) {
        this.codeCentre = code;
    }

    /**
     * Méthode unifiée pour getLibelle() - REQUIS PAR ReferentielController
     */
    public String getLibelle() {
        return nomCentre;
    }

    /**
     * Méthode unifiée pour setLibelle() - REQUIS PAR ReferentielController
     */
    public void setLibelle(String libelle) {
        this.nomCentre = libelle;
    }

    /**
     * Méthode pour getDescription() - REQUIS PAR ReferentielController
     */
    public String getDescription() {
        return description;
    }

    /**
     * Méthode pour setDescription() - REQUIS PAR ReferentielController
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Méthode pour isActif() - REQUIS PAR ReferentielController
     */
    public boolean isActif() {
        return actif;
    }

    /**
     * Méthode pour setActif() - REQUIS PAR ReferentielController
     */
    public void setActif(boolean actif) {
        this.actif = actif;
    }

    // ===== GETTERS ET SETTERS CLASSIQUES =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCodeCentre() { return codeCentre; }
    public void setCodeCentre(String codeCentre) { this.codeCentre = codeCentre; }

    public String getNomCentre() { return nomCentre; }
    public void setNomCentre(String nomCentre) { this.nomCentre = nomCentre; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Centre centre = (Centre) o;
        return Objects.equals(id, centre.id) && Objects.equals(codeCentre, centre.codeCentre);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, codeCentre);
    }

    @Override
    public String toString() {
        return codeCentre + " - " + nomCentre;
    }
}