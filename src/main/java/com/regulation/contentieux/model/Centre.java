package com.regulation.contentieux.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entité représentant un centre
 * Un centre regroupe plusieurs services
 */
public class Centre {
    private Long id;
    private String codeCentre;
    private String nomCentre;
    private String description;
    private String adresse;
    private Boolean actif;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // Relations
    @JsonIgnore
    private List<Service> services = new ArrayList<>();

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

    // Méthodes métier
    public String getCode() {
        return codeCentre;
    }

    public void setCode(String code) {
        this.codeCentre = code;
    }

    public String getLibelle() {
        return nomCentre;
    }

    public void setLibelle(String libelle) {
        this.nomCentre = libelle;
    }

    public boolean isActif() {
        return actif != null && actif;
    }

    public int getNombreServices() {
        return services.size();
    }

    public String getDisplayName() {
        return codeCentre + " - " + nomCentre;
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCodeCentre() { return codeCentre; }
    public void setCodeCentre(String codeCentre) { this.codeCentre = codeCentre; }

    public String getNomCentre() { return nomCentre; }
    public void setNomCentre(String nomCentre) { this.nomCentre = nomCentre; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }

    public Boolean getActif() { return actif; }
    public void setActif(Boolean actif) { this.actif = actif; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<Service> getServices() { return services; }
    public void setServices(List<Service> services) { this.services = services; }

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