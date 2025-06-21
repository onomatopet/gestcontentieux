package com.regulation.contentieux.model.enums;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Énumération des rôles utilisateur avec leurs permissions
 * Gère la hiérarchie des autorisations dans l'application
 */
public enum RoleUtilisateur {

    SUPER_ADMIN("Super Administrateur",
            Permission.ALL),

    ADMINISTRATEUR("Administrateur",
            Permission.GESTION_UTILISATEURS,
            Permission.GESTION_REFERENTIEL,
            Permission.GESTION_AFFAIRES,
            Permission.GESTION_AGENTS,
            Permission.GESTION_CONTREVENANTS,
            Permission.GESTION_ENCAISSEMENTS,
            Permission.VALIDATION_ENCAISSEMENTS,
            Permission.GENERATION_RAPPORTS,
            Permission.EXPORT_DONNEES,
            Permission.CONSULTATION),

    CHEF_SERVICE("Chef de Service",
            Permission.GESTION_AFFAIRES,
            Permission.GESTION_AGENTS,
            Permission.GESTION_CONTREVENANTS,
            Permission.GESTION_ENCAISSEMENTS,
            Permission.VALIDATION_ENCAISSEMENTS,
            Permission.GENERATION_RAPPORTS,
            Permission.EXPORT_DONNEES,
            Permission.CONSULTATION),

    AGENT_SAISIE("Agent de Saisie",
            Permission.GESTION_AFFAIRES,
            Permission.GESTION_CONTREVENANTS,
            Permission.GESTION_ENCAISSEMENTS,
            Permission.CONSULTATION),

    AGENT_CONSULTATION("Agent de Consultation",
            Permission.CONSULTATION,
            Permission.GENERATION_RAPPORTS);

    private final String libelle;
    private final Set<Permission> permissions;

    RoleUtilisateur(String libelle, Permission... permissions) {
        this.libelle = libelle;
        this.permissions = new HashSet<>(Arrays.asList(permissions));
    }

    /**
     * Retourne le libellé du rôle
     */
    public String getLibelle() {
        return libelle;
    }

    /**
     * Vérifie si le rôle a une permission spécifique
     */
    public boolean hasPermission(Permission permission) {
        return permissions.contains(Permission.ALL) || permissions.contains(permission);
    }

    /**
     * Vérifie si le rôle a une permission par son nom
     */
    public boolean hasPermission(String permissionName) {
        try {
            Permission permission = Permission.valueOf(permissionName);
            return hasPermission(permission);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Retourne l'ensemble des permissions du rôle
     */
    public Set<Permission> getPermissions() {
        return new HashSet<>(permissions);
    }

    /**
     * Énumération des permissions disponibles
     */
    public enum Permission {
        ALL("Toutes les permissions"),
        GESTION_UTILISATEURS("Gestion des utilisateurs"),
        GESTION_REFERENTIEL("Gestion du référentiel"),
        GESTION_AFFAIRES("Gestion des affaires"),
        GESTION_AGENTS("Gestion des agents"),
        GESTION_CONTREVENANTS("Gestion des contrevenants"),
        GESTION_ENCAISSEMENTS("Gestion des encaissements"),
        VALIDATION_ENCAISSEMENTS("Validation des encaissements"),
        GENERATION_RAPPORTS("Génération des rapports"),
        EXPORT_DONNEES("Export des données"),
        CONSULTATION("Consultation");

        private final String description;

        Permission(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    @Override
    public String toString() {
        return libelle;
    }
}