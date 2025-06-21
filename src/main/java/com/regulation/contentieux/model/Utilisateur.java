package com.regulation.contentieux.model;

import com.regulation.contentieux.model.enums.RoleUtilisateur;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entité représentant un utilisateur du système
 * Gère l'authentification et les autorisations
 */
public class Utilisateur {

    private Long id;
    private String login;
    private String motDePasse;
    private String nom;
    private String prenom;
    private String nomComplet;
    private String email;
    private RoleUtilisateur role;
    private boolean actif;
    private LocalDateTime dateCreation;
    private LocalDateTime derniereConnexion;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;

    // Constructeurs
    public Utilisateur() {
        this.actif = true;
        this.dateCreation = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }

    public Utilisateur(String login, String motDePasse, String nom, String prenom, RoleUtilisateur role) {
        this();
        this.login = login;
        this.motDePasse = motDePasse;
        this.nom = nom;
        this.prenom = prenom;
        this.role = role;
        updateNomComplet();
    }

    // Méthodes métier

    /**
     * Met à jour le nom complet basé sur le nom et prénom
     */
    private void updateNomComplet() {
        if (nom != null && prenom != null) {
            this.nomComplet = prenom + " " + nom;
        } else if (nom != null) {
            this.nomComplet = nom;
        } else if (prenom != null) {
            this.nomComplet = prenom;
        } else {
            this.nomComplet = login;
        }
    }

    /**
     * Vérifie si l'utilisateur a une permission spécifique
     */
    public boolean hasPermission(String permission) {
        return role != null && role.hasPermission(permission);
    }

    /**
     * Retourne le nom d'affichage de l'utilisateur
     */
    public String getDisplayName() {
        if (nomComplet != null && !nomComplet.trim().isEmpty()) {
            return nomComplet;
        }
        return login;
    }

    /**
     * Retourne le nom d'utilisateur pour l'authentification
     */
    public String getUsername() {
        return login;
    }

    // Getters et Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
        if (this.nomComplet == null || this.nomComplet.equals(this.login)) {
            updateNomComplet();
        }
    }

    public String getMotDePasse() {
        return motDePasse;
    }

    public void setMotDePasse(String motDePasse) {
        this.motDePasse = motDePasse;
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

    public String getNomComplet() {
        return nomComplet;
    }

    public void setNomComplet(String nomComplet) {
        this.nomComplet = nomComplet;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public RoleUtilisateur getRole() {
        return role;
    }

    public void setRole(RoleUtilisateur role) {
        this.role = role;
    }

    public boolean isActif() {
        return actif;
    }

    public void setActif(boolean actif) {
        this.actif = actif;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }

    public LocalDateTime getDerniereConnexion() {
        return derniereConnexion;
    }

    public void setDerniereConnexion(LocalDateTime derniereConnexion) {
        this.derniereConnexion = derniereConnexion;
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

    // Equals et HashCode

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Utilisateur that = (Utilisateur) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(login, that.login);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, login);
    }

    @Override
    public String toString() {
        return "Utilisateur{" +
                "id=" + id +
                ", login='" + login + '\'' +
                ", nom='" + nom + '\'' +
                ", prenom='" + prenom + '\'' +
                ", role=" + role +
                ", actif=" + actif +
                '}';
    }
}