package com.regulation.contentieux.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entité représentant un bureau
 * HARMONISÉE AVEC ReferentielController
 */
public class Bureau {
    private Long id;
    private String codeBureau;
    private String nomBureau;
    private String description;
    private boolean actif = true;
    private Service service; // Relation parent

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // Constructeurs
    public Bureau() {
        this.createdAt = LocalDateTime.now();
        this.actif = true;
    }

    public Bureau(String codeBureau, String nomBureau) {
        this();
        this.codeBureau = codeBureau;
        this.nomBureau = nomBureau;
    }

    // ===== MÉTHODES REQUISES PAR ReferentielController =====

    /**
     * Méthode unifiée pour getCode() - REQUIS PAR ReferentielController
     */
    public String getCode() {
        return codeBureau;
    }

    /**
     * Méthode unifiée pour setCode() - REQUIS PAR ReferentielController
     */
    public void setCode(String code) {
        this.codeBureau = code;
    }

    /**
     * Méthode unifiée pour getLibelle() - REQUIS PAR ReferentielController
     */
    public String getLibelle() {
        return nomBureau;
    }

    /**
     * Méthode unifiée pour setLibelle() - REQUIS PAR ReferentielController
     */
    public void setLibelle(String libelle) {
        this.nomBureau = libelle;
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

    public String getCodeBureau() { return codeBureau; }
    public void setCodeBureau(String codeBureau) { this.codeBureau = codeBureau; }

    public String getNomBureau() { return nomBureau; }
    public void setNomBureau(String nomBureau) { this.nomBureau = nomBureau; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Service getService() { return service; }
    public void setService(Service service) { this.service = service; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bureau bureau = (Bureau) o;
        return Objects.equals(id, bureau.id) && Objects.equals(codeBureau, bureau.codeBureau);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, codeBureau);
    }

    @Override
    public String toString() {
        return codeBureau + " - " + nomBureau;
    }
}