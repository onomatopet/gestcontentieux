package com.regulation.contentieux.model;

import com.regulation.contentieux.model.enums.RoleUtilisateur;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entité représentant un utilisateur du système
 * Gère l'authentification et les autorisations
 */
public class Utilisateur {
    private Long id;
    private String username;

    @JsonIgnore
    private String passwordHash;

    private String nom;
    private String prenom;
    private String email;
    private RoleUtilisateur role;
    private Boolean actif;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginAt;

    private String createdBy;
    private String updatedBy;

    // Constructeurs
    public Utilisateur() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.actif = true;
        this.role = RoleUtilisateur.GESTIONNAIRE;
    }

    public Utilisateur(String username, String nom, String prenom, RoleUtilisateur role) {
        this();
        this.username = username;
        this.nom = nom;
        this.prenom = prenom;
        this.role = role;
    }

    // Méthodes métier
    public String getNomComplet() {
        return prenom + " " + nom;
    }

    public String getDisplayName() {
        if (prenom != null && nom != null) {
            return prenom + " " + nom.toUpperCase();
        }
        return username;
    }

    public boolean isActif() {
        return actif != null && actif;
    }

    public boolean isSuperAdmin() {
        return role == RoleUtilisateur.SUPER_ADMIN;
    }

    public boolean isAdmin() {
        return role == RoleUtilisateur.ADMIN || isSuperAdmin();
    }

    public boolean isGestionnaire() {
        return role == RoleUtilisateur.GESTIONNAIRE;
    }

    public boolean hasPermission(String permission) {
        return role != null && role.hasPermission(permission);
    }

    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public RoleUtilisateur getRole() { return role; }
    public void setRole(RoleUtilisateur role) { this.role = role; }

    public Boolean getActif() { return actif; }
    public void setActif(Boolean actif) { this.actif = actif; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    // Méthodes helper pour l'interface
    public LocalDateTime getDerniereConnexion() {
        return lastLoginAt;
    }

    public String getLogin() {
        return username;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Utilisateur that = (Utilisateur) o;
        return Objects.equals(id, that.id) && Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username);
    }

    @Override
    public String toString() {
        return username + " (" + getDisplayName() + ")";
    }
}