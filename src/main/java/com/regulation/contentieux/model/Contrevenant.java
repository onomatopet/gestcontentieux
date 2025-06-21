package com.regulation.contentieux.model;

import com.regulation.contentieux.model.enums.TypeContrevenant;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entité représentant un contrevenant
 * Peut être une personne physique ou morale
 */
public class Contrevenant {

    private Long id;
    private TypeContrevenant type;

    // Personne physique
    private String nom;
    private String prenom;
    private String cin;

    // Personne morale
    private String raisonSociale;
    private String numeroRegistreCommerce;
    private String numeroIdentificationFiscale;

    // Informations communes
    private String adresse;
    private String telephone;
    private String email;
    private String ville;
    private String codePostal;
    private boolean actif;

    // Métadonnées
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;

    // Constructeurs
    public Contrevenant() {
        this.actif = true;
        this.createdAt = LocalDateTime.now();
    }

    // Constructeur pour personne physique
    public Contrevenant(String nom, String prenom, String cin) {
        this();
        this.type = TypeContrevenant.PERSONNE_PHYSIQUE;
        this.nom = nom;
        this.prenom = prenom;
        this.cin = cin;
    }

    // Constructeur pour personne morale
    public Contrevenant(String raisonSociale, String numeroRegistreCommerce) {
        this();
        this.type = TypeContrevenant.PERSONNE_MORALE;
        this.raisonSociale = raisonSociale;
        this.numeroRegistreCommerce = numeroRegistreCommerce;
    }

    // Méthodes métier

    /**
     * Retourne le nom d'affichage selon le type
     */
    public String getDisplayName() {
        if (type == TypeContrevenant.PERSONNE_PHYSIQUE) {
            StringBuilder display = new StringBuilder();
            if (nom != null) {
                display.append(nom);
            }
            if (prenom != null) {
                if (display.length() > 0) display.append(" ");
                display.append(prenom);
            }
            if (cin != null) {
                display.append(" (CIN: ").append(cin).append(")");
            }
            return display.toString();
        } else if (type == TypeContrevenant.PERSONNE_MORALE) {
            StringBuilder display = new StringBuilder();
            if (raisonSociale != null) {
                display.append(raisonSociale);
            }
            if (numeroRegistreCommerce != null) {
                display.append(" (RC: ").append(numeroRegistreCommerce).append(")");
            }
            return display.toString();
        }
        return "Contrevenant non défini";
    }

    /**
     * Retourne le nom court
     */
    public String getShortName() {
        if (type == TypeContrevenant.PERSONNE_PHYSIQUE) {
            return nom != null ? nom : "N/A";
        } else {
            return raisonSociale != null ? raisonSociale : "N/A";
        }
    }

    /**
     * Retourne l'identifiant principal selon le type
     */
    public String getIdentifiantPrincipal() {
        if (type == TypeContrevenant.PERSONNE_PHYSIQUE) {
            return cin;
        } else {
            return numeroRegistreCommerce;
        }
    }

    /**
     * Vérifie si toutes les informations obligatoires sont renseignées
     */
    public boolean isComplete() {
        if (type == TypeContrevenant.PERSONNE_PHYSIQUE) {
            return nom != null && !nom.trim().isEmpty();
        } else {
            return raisonSociale != null && !raisonSociale.trim().isEmpty();
        }
    }

    /**
     * Retourne l'adresse complète
     */
    public String getAdresseComplete() {
        StringBuilder addr = new StringBuilder();
        if (adresse != null && !adresse.trim().isEmpty()) {
            addr.append(adresse);
        }
        if (codePostal != null && !codePostal.trim().isEmpty()) {
            if (addr.length() > 0) addr.append(", ");
            addr.append(codePostal);
        }
        if (ville != null && !ville.trim().isEmpty()) {
            if (addr.length() > 0) addr.append(" ");
            addr.append(ville);
        }
        return addr.toString();
    }

    // Getters et Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TypeContrevenant getType() {
        return type;
    }

    public void setType(TypeContrevenant type) {
        this.type = type;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public String getCin() {
        return cin;
    }

    public void setCin(String cin) {
        this.cin = cin;
    }

    public String getRaisonSociale() {
        return raisonSociale;
    }

    public void setRaisonSociale(String raisonSociale) {
        this.raisonSociale = raisonSociale;
    }

    public String getNumeroRegistreCommerce() {
        return numeroRegistreCommerce;
    }

    public void setNumeroRegistreCommerce(String numeroRegistreCommerce) {
        this.numeroRegistreCommerce = numeroRegistreCommerce;
    }

    public String getNumeroIdentificationFiscale() {
        return numeroIdentificationFiscale;
    }

    public void setNumeroIdentificationFiscale(String numeroIdentificationFiscale) {
        this.numeroIdentificationFiscale = numeroIdentificationFiscale;
    }

    public String getAdresse() {
        return adresse;
    }

    public void setAdresse(String adresse) {
        this.adresse = adresse;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getVille() {
        return ville;
    }

    public void setVille(String ville) {
        this.ville = ville;
    }

    public String getCodePostal() {
        return codePostal;
    }

    public void setCodePostal(String codePostal) {
        this.codePostal = codePostal;
    }

    public boolean isActif() {
        return actif;
    }

    public void setActif(boolean actif) {
        this.actif = actif;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Equals, HashCode et ToString

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Contrevenant that = (Contrevenant) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Contrevenant{" +
                "id=" + id +
                ", type=" + type +
                ", displayName='" + getDisplayName() + '\'' +
                ", actif=" + actif +
                '}';
    }
}