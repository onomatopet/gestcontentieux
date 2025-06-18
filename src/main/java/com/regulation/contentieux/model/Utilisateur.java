package com.regulation.contentieux.model;

import com.regulation.contentieux.model.enums.RoleUtilisateur;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entité représentant un utilisateur du système
 */
public class Utilisateur {
    private Long id;
    private String username;

    @JsonIgnore
    private String passwordHash;

    private String nomComplet;
    private RoleUtilisateur role;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginAt;

    private Boolean actif;

    // Constructeurs
    public Utilisateur() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.actif = true;
    }

    public Utilisateur(String username, String passwordHash, String nomComplet, RoleUtilisateur role) {
        this();
        this.username = username;
        this.passwordHash = passwordHash;
        this.nomComplet = nomComplet;
        this.role = role;
    }

    // Méthodes métier
    public boolean isAdmin() {
        return role != null && role.isAdmin();
    }

    public boolean isSuperAdmin() {
        return role != null && role.isSuperAdmin();
    }

    public boolean isActif() {
        return actif != null && actif;
    }

    public boolean hasPermission(RoleUtilisateur.Permission permission) {
        return role != null && role.hasPermission(permission);
    }

    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getDisplayRole() {
        return role != null ? role.getDisplayName() : "Aucun rôle";
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getNomComplet() { return nomComplet; }
    public void setNomComplet(String nomComplet) { this.nomComplet = nomComplet; }

    public RoleUtilisateur getRole() { return role; }
    public void setRole(RoleUtilisateur role) {
        this.role = role;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public Boolean getActif() { return actif; }
    public void setActif(Boolean actif) {
        this.actif = actif;
        this.updatedAt = LocalDateTime.now();
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
        return username + " (" + nomComplet + ") - " + getDisplayRole();
    }
}