package com.regulation.contentieux.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entité représentant une banque
 * HARMONISÉE AVEC ReferentielController
 */
public class Banque {
    private Long id;
    private String codeBanque;
    private String nomBanque;
    private String description;
    private boolean actif = true;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // Constructeurs
    public Banque() {
        this.createdAt = LocalDateTime.now();
        this.actif = true;
    }

    public Banque(String codeBanque, String nomBanque) {
        this();
        this.codeBanque = codeBanque;
        this.nomBanque = nomBanque;
    }

    // ===== MÉTHODES REQUISES PAR ReferentielController =====

    /**
     * Méthode unifiée pour getCode() - REQUIS PAR ReferentielController
     */
    public String getCode() {
        return codeBanque;
    }

    /**
     * Méthode unifiée pour setCode() - REQUIS PAR ReferentielController
     */
    public void setCode(String code) {
        this.codeBanque = code;
    }

    /**
     * Méthode unifiée pour getLibelle() - REQUIS PAR ReferentielController
     */
    public String getLibelle() {
        return nomBanque;
    }

    /**
     * Méthode unifiée pour setLibelle() - REQUIS PAR ReferentielController
     */
    public void setLibelle(String libelle) {
        this.nomBanque = libelle;
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

    public String getCodeBanque() { return codeBanque; }
    public void setCodeBanque(String codeBanque) { this.codeBanque = codeBanque; }

    public String getNomBanque() { return nomBanque; }
    public void setNomBanque(String nomBanque) { this.nomBanque = nomBanque; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Banque banque = (Banque) o;
        return Objects.equals(id, banque.id) && Objects.equals(codeBanque, banque.codeBanque);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, codeBanque);
    }

    @Override
    public String toString() {
        return codeBanque + " - " + nomBanque;
    }
}