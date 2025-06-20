package com.regulation.contentieux.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entité représentant un bureau
 * Un bureau appartient à un service
 */
public class Bureau {
    private Long id;
    private String codeBureau;
    private String nomBureau;
    private String description;
    private Boolean actif;
    private Long serviceId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // Relations
    @JsonIgnore
    private Service service;

    @JsonIgnore
    private List<Affaire> affaires = new ArrayList<>();

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

    // Méthodes métier
    public String getCode() {
        return codeBureau;
    }

    public void setCode(String code) {
        this.codeBureau = code;
    }

    public String getLibelle() {
        return nomBureau;
    }

    public void setLibelle(String libelle) {
        this.nomBureau = libelle;
    }

    public boolean isActif() {
        return actif != null && actif;
    }

    public int getNombreAffaires() {
        return affaires.size();
    }

    public String getDisplayName() {
        return codeBureau + " - " + nomBureau;
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCodeBureau() { return codeBureau; }
    public void setCodeBureau(String codeBureau) { this.codeBureau = codeBureau; }

    public String getNomBureau() { return nomBureau; }
    public void setNomBureau(String nomBureau) { this.nomBureau = nomBureau; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getActif() { return actif; }
    public void setActif(Boolean actif) { this.actif = actif; }

    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long serviceId) { this.serviceId = serviceId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Service getService() { return service; }
    public void setService(Service service) {
        this.service = service;
        if (service != null) {
            this.serviceId = service.getId();
        }
    }

    public List<Affaire> getAffaires() { return affaires; }
    public void setAffaires(List<Affaire> affaires) { this.affaires = affaires; }

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