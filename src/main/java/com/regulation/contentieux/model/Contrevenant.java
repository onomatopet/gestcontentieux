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
    private String code; // Ajout du champ code
    private TypeContrevenant type;
    private String nomComplet; // Ajout du champ nomComplet
    private String typePersonne; // Ajout pour compatibilité avec DAO existant

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
        this.typePersonne = "PHYSIQUE";
        this.nom = nom;
        this.prenom = prenom;
        this.cin = cin;
        updateNomComplet();
    }

    // Constructeur pour personne morale
    public Contrevenant(String raisonSociale, String numeroRegistreCommerce) {
        this();
        this.type = TypeContrevenant.PERSONNE_MORALE;
        this.typePersonne = "MORALE";
        this.raisonSociale = raisonSociale;
        this.numeroRegistreCommerce = numeroRegistreCommerce;
        updateNomComplet();
    }

    // Méthodes métier

    /**
     * Met à jour le nom complet basé sur le type de contrevenant
     */
    private void updateNomComplet() {
        if (type == TypeContrevenant.PERSONNE_PHYSIQUE || "PHYSIQUE".equals(typePersonne)) {
            if (nom != null && prenom != null) {
                this.nomComplet = prenom + " " + nom;
            } else if (nom != null) {
                this.nomComplet = nom;
            } else if (prenom != null) {
                this.nomComplet = prenom;
            }
        } else if (type == TypeContrevenant.PERSONNE_MORALE || "MORALE".equals(typePersonne)) {
            this.nomComplet = raisonSociale;
        }
    }

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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public TypeContrevenant getType() {
        return type;
    }

    public void setType(TypeContrevenant type) {
        this.type = type;
        // Synchroniser typePersonne avec type
        if (type == TypeContrevenant.PERSONNE_PHYSIQUE) {
            this.typePersonne = "PHYSIQUE";
        } else if (type == TypeContrevenant.PERSONNE_MORALE) {
            this.typePersonne = "MORALE";
        }
        updateNomComplet();
    }

    public String getNomComplet() {
        if (nomComplet == null) {
            updateNomComplet();
        }
        return nomComplet;
    }

    public void setNomComplet(String nomComplet) {
        this.nomComplet = nomComplet;
    }

    public String getTypePersonne() {
        return typePersonne;
    }

    public void setTypePersonne(String typePersonne) {
        this.typePersonne = typePersonne;
        // Synchroniser type avec typePersonne
        if ("PHYSIQUE".equals(typePersonne)) {
            this.type = TypeContrevenant.PERSONNE_PHYSIQUE;
        } else if ("MORALE".equals(typePersonne)) {
            this.type = TypeContrevenant.PERSONNE_MORALE;
        }
        updateNomComplet();
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
        updateNomComplet();
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
        updateNomComplet();
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
        updateNomComplet();
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
        return getDisplayName();
    }
}