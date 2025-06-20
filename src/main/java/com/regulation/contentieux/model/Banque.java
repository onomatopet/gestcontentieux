package com.regulation.contentieux.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entité représentant une banque
 * Utilisée pour gérer les encaissements bancaires
 */
public class Banque {
    private Long id;
    private String codeBanque;
    private String nomBanque;
    private String description;
    private String adresse;
    private String telephone;
    private String email;
    private Boolean actif;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // Relations
    @JsonIgnore
    private List<Encaissement> encaissements = new ArrayList<>();

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

    // Méthodes métier
    public String getCode() {
        return codeBanque;
    }

    public void setCode(String code) {
        this.codeBanque = code;
    }

    public String getLibelle() {
        return nomBanque;
    }

    public void setLibelle(String libelle) {
        this.nomBanque = libelle;
    }

    public boolean isActif() {
        return actif != null && actif;
    }

    public int getNombreEncaissements() {
        return encaissements.size();
    }

    public String getDisplayName() {
        return codeBanque + " - " + nomBanque;
    }

    public String getContactInfo() {
        StringBuilder info = new StringBuilder();
        if (telephone != null) info.append("Tél: ").append(telephone);
        if (email != null) {
            if (info.length() > 0) info.append(" | ");
            info.append("Email: ").append(email);
        }
        return info.toString();
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCodeBanque() { return codeBanque; }
    public void setCodeBanque(String codeBanque) { this.codeBanque = codeBanque; }

    public String getNomBanque() { return nomBanque; }
    public void setNomBanque(String nomBanque) { this.nomBanque = nomBanque; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }

    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Boolean getActif() { return actif; }
    public void setActif(Boolean actif) { this.actif = actif; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<Encaissement> getEncaissements() { return encaissements; }
    public void setEncaissements(List<Encaissement> encaissements) { this.encaissements = encaissements; }

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