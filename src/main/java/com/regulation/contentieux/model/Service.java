package com.regulation.contentieux.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entité représentant un service
 * HARMONISÉE AVEC ReferentielController
 */
public class Service {
    private Long id;
    private String codeService;
    private String nomService;
    private String description;
    private boolean actif = true;
    private Centre centre; // Relation parent

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // Constructeurs
    public Service() {
        this.createdAt = LocalDateTime.now();
        this.actif = true;
    }

    public Service(String codeService, String nomService) {
        this();
        this.codeService = codeService;
        this.nomService = nomService;
    }

    // ===== MÉTHODES REQUISES PAR ReferentielController =====

    /**
     * Méthode unifiée pour getCode() - REQUIS PAR ReferentielController
     */
    public String getCode() {
        return codeService;
    }

    /**
     * Méthode unifiée pour setCode() - REQUIS PAR ReferentielController
     */
    public void setCode(String code) {
        this.codeService = code;
    }

    /**
     * Méthode unifiée pour getLibelle() - REQUIS PAR ReferentielController
     */
    public String getLibelle() {
        return nomService;
    }

    /**
     * Méthode unifiée pour setLibelle() - REQUIS PAR ReferentielController
     */
    public void setLibelle(String libelle) {
        this.nomService = libelle;
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

    public String getCodeService() { return codeService; }
    public void setCodeService(String codeService) { this.codeService = codeService; }

    public String getNomService() { return nomService; }
    public void setNomService(String nomService) { this.nomService = nomService; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Centre getCentre() { return centre; }
    public void setCentre(Centre centre) { this.centre = centre; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Service service = (Service) o;
        return Objects.equals(id, service.id) && Objects.equals(codeService, service.codeService);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, codeService);
    }

    @Override
    public String toString() {
        return codeService + " - " + nomService;
    }
}