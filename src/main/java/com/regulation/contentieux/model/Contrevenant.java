package com.regulation.contentieux.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class Contrevenant {
    private Long id;
    private String code;
    private String nomComplet;
    private String adresse;
    private String telephone;
    private String email;
    private String typePersonne; // PHYSIQUE ou MORALE

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    // Relations
    @JsonIgnore
    private List<Affaire> affaires = new ArrayList<>();

    // Constructeurs
    public Contrevenant() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Contrevenant(String code, String nomComplet) {
        this();
        this.code = code;
        this.nomComplet = nomComplet;
    }

    // Méthodes métier
    public String getFormattedAddress() {
        return adresse != null ? adresse.replace("\n", ", ") : "";
    }

    public boolean isPersonnePhysique() {
        return "PHYSIQUE".equals(typePersonne);
    }

    public boolean isPersonneMorale() {
        return "MORALE".equals(typePersonne);
    }

    public int getNombreAffaires() {
        return affaires.size();
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getNomComplet() { return nomComplet; }
    public void setNomComplet(String nomComplet) { this.nomComplet = nomComplet; }

    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }

    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTypePersonne() { return typePersonne; }
    public void setTypePersonne(String typePersonne) { this.typePersonne = typePersonne; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<Affaire> getAffaires() { return affaires; }
    public void setAffaires(List<Affaire> affaires) { this.affaires = affaires; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Contrevenant that = (Contrevenant) o;
        return Objects.equals(id, that.id) && Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, code);
    }

    @Override
    public String toString() {
        return code + " - " + nomComplet;
    }
}
