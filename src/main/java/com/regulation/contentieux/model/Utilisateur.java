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

    public class Utilisateur {
        // Champs existants...
        private String login;
        private String nom;
        private String prenom;
        private String email;
        private String motDePasseHash;
        private String motDePasse; // Champ temporaire pour la création/modification
        private RoleUtilisateur role;
        private boolean actif;
        private LocalDateTime createdAt;
        private String createdBy;
        private LocalDateTime updatedAt;
        private String updatedBy;
        private LocalDateTime dateCreation; // Alias pour createdAt si nécessaire

        // Getters et setters manquants :

        public String getLogin() {
            return login;
        }

        public void setLogin(String login) {
            this.login = login;
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

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getMotDePasse() {
            return motDePasse;
        }

        public void setMotDePasse(String motDePasse) {
            this.motDePasse = motDePasse;
        }

        public String getMotDePasseHash() {
            return motDePasseHash;
        }

        public void setMotDePasseHash(String motDePasseHash) {
            this.motDePasseHash = motDePasseHash;
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

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public String getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(String createdBy) {
            this.createdBy = createdBy;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }

        public String getUpdatedBy() {
            return updatedBy;
        }

        public void setUpdatedBy(String updatedBy) {
            this.updatedBy = updatedBy;
        }

        /**
         * Alias pour la compatibilité
         */
        public LocalDateTime getDateCreation() {
            return this.createdAt;
        }

        public void setDateCreation(LocalDateTime dateCreation) {
            this.createdAt = dateCreation;
        }

        // Méthodes utilitaires

        /**
         * Retourne le nom complet de l'utilisateur
         */
        public String getNomComplet() {
            StringBuilder nomComplet = new StringBuilder();
            if (prenom != null && !prenom.trim().isEmpty()) {
                nomComplet.append(prenom.trim());
            }
            if (nom != null && !nom.trim().isEmpty()) {
                if (nomComplet.length() > 0) {
                    nomComplet.append(" ");
                }
                nomComplet.append(nom.trim());
            }
            return nomComplet.length() > 0 ? nomComplet.toString() : login;
        }

        /**
         * Vérifie si l'utilisateur est un administrateur
         */
        public boolean isAdmin() {
            return role != null && role == RoleUtilisateur.ADMIN;
        }

        /**
         * Vérifie si l'utilisateur peut gérer d'autres utilisateurs
         */
        public boolean canManageUsers() {
            return role != null && (role == RoleUtilisateur.ADMIN || role == RoleUtilisateur.SUPERVISEUR);
        }

        @Override
        public String toString() {
            return getNomComplet() + " (" + login + ")";
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Utilisateur that = (Utilisateur) obj;
            return Objects.equals(login, that.login);
        }

        @Override
        public int hashCode() {
            return Objects.hash(login);
        }
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

    public class Utilisateur {
        // Champs existants...
        private String login;
        private String nom;
        private String prenom;
        private String email;
        private String motDePasseHash;
        private String motDePasse; // Champ temporaire pour la création/modification
        private RoleUtilisateur role;
        private boolean actif;
        private LocalDateTime createdAt;
        private String createdBy;
        private LocalDateTime updatedAt;
        private String updatedBy;
        private LocalDateTime dateCreation; // Alias pour createdAt si nécessaire

        // Getters et setters manquants :

        public String getLogin() {
            return login;
        }

        public void setLogin(String login) {
            this.login = login;
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

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getMotDePasse() {
            return motDePasse;
        }

        public void setMotDePasse(String motDePasse) {
            this.motDePasse = motDePasse;
        }

        public String getMotDePasseHash() {
            return motDePasseHash;
        }

        public void setMotDePasseHash(String motDePasseHash) {
            this.motDePasseHash = motDePasseHash;
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

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public String getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(String createdBy) {
            this.createdBy = createdBy;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }

        public String getUpdatedBy() {
            return updatedBy;
        }

        public void setUpdatedBy(String updatedBy) {
            this.updatedBy = updatedBy;
        }

        /**
         * Alias pour la compatibilité
         */
        public LocalDateTime getDateCreation() {
            return this.createdAt;
        }

        public void setDateCreation(LocalDateTime dateCreation) {
            this.createdAt = dateCreation;
        }

        // Méthodes utilitaires

        /**
         * Retourne le nom complet de l'utilisateur
         */
        public String getNomComplet() {
            StringBuilder nomComplet = new StringBuilder();
            if (prenom != null && !prenom.trim().isEmpty()) {
                nomComplet.append(prenom.trim());
            }
            if (nom != null && !nom.trim().isEmpty()) {
                if (nomComplet.length() > 0) {
                    nomComplet.append(" ");
                }
                nomComplet.append(nom.trim());
            }
            return nomComplet.length() > 0 ? nomComplet.toString() : login;
        }

        /**
         * Vérifie si l'utilisateur est un administrateur
         */
        public boolean isAdmin() {
            return role != null && role == RoleUtilisateur.ADMIN;
        }

        /**
         * Vérifie si l'utilisateur peut gérer d'autres utilisateurs
         */
        public boolean canManageUsers() {
            return role != null && (role == RoleUtilisateur.ADMIN || role == RoleUtilisateur.SUPERVISEUR);
        }

        @Override
        public String toString() {
            return getNomComplet() + " (" + login + ")";
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Utilisateur that = (Utilisateur) obj;
            return Objects.equals(login, that.login);
        }

        @Override
        public int hashCode() {
            return Objects.hash(login);
        }
    }
}