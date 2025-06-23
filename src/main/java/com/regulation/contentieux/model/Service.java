package com.regulation.contentieux.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entité représentant un service * appartient à un centre et contient plusieurs bureaux
 */
public class Service {
    private Long id;
    private String codeService;
    private String nomService;
    private String description;
    private Boolean actif;
    private Long centreId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // Relations
    @JsonIgnore
    private Centre centre;

    @JsonIgnore
    private List<Bureau> bureaux = new ArrayList<>();

    @JsonIgnore
    private List<Agent> agents = new ArrayList<>();

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

    // Méthodes métier
    public String getCode() {
        return codeService;
    }

    public void setCode(String code) {
        this.codeService = code;
    }

    public String getLibelle() {
        return nomService;
    }

    public void setLibelle(String libelle) {
        this.nomService = libelle;
    }

    public boolean isActif() {
        return actif != null && actif;
    }

    public int getNombreBureaux() {
        return bureaux.size();
    }

    public int getNombreAgents() {
        return agents.size();
    }

    public String getDisplayName() {
        return codeService + " - " + nomService;
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCodeService() { return codeService; }
    public void setCodeService(String codeService) { this.codeService = codeService; }

    public String getNomService() { return nomService; }
    public void setNomService(String nomService) { this.nomService = nomService; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getActif() { return actif; }
    public void setActif(Boolean actif) { this.actif = actif; }

    public Long getCentreId() { return centreId; }
    public void setCentreId(Long centreId) { this.centreId = centreId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Centre getCentre() { return centre; }
    public void setCentre(Centre centre) {
        this.centre = centre;
        if (centre != null) {
            this.centreId = centre.getId();
        }
    }

    public List<Bureau> getBureaux() { return bureaux; }
    public void setBureaux(List<Bureau> bureaux) { this.bureaux = bureaux; }

    public List<Agent> getAgents() { return agents; }
    public void setAgents(List<Agent> agents) { this.agents = agents; }

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