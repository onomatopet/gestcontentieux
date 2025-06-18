package com.regulation.contentieux.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.regulation.contentieux.model.enums.StatutAffaire;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class Agent {
    private Long id;
    private String codeAgent;
    private String nom;
    private String prenom;
    private String grade;
    private Long serviceId;
    private Boolean actif;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    // Relations
    @JsonIgnore
    private List<AffaireActeur> affaireActeurs = new ArrayList<>();

    // Constructeurs
    public Agent() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.actif = true;
    }

    public Agent(String codeAgent, String nom, String prenom) {
        this();
        this.codeAgent = codeAgent;
        this.nom = nom;
        this.prenom = prenom;
    }

    // Méthodes métier
    public String getNomComplet() {
        return prenom + " " + nom;
    }

    public String getNomFormate() {
        return nom.toUpperCase() + " " + prenom;
    }

    public boolean isActif() {
        return actif != null && actif;
    }

    public int getChargesTravail() {
        return affaireActeurs.size(); // Simplifié
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCodeAgent() { return codeAgent; }
    public void setCodeAgent(String codeAgent) { this.codeAgent = codeAgent; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long serviceId) { this.serviceId = serviceId; }

    public Boolean getActif() { return actif; }
    public void setActif(Boolean actif) { this.actif = actif; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<AffaireActeur> getAffaireActeurs() { return affaireActeurs; }
    public void setAffaireActeurs(List<AffaireActeur> affaireActeurs) { this.affaireActeurs = affaireActeurs; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Agent agent = (Agent) o;
        return Objects.equals(id, agent.id) && Objects.equals(codeAgent, agent.codeAgent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, codeAgent);
    }

    @Override
    public String toString() {
        return codeAgent + " - " + getNomComplet();
    }
}