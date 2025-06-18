package com.regulation.contentieux.model.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Énumération des rôles utilisateur dans l'application
 * Définit les permissions et la hiérarchie des utilisateurs
 *
 * @author Regulation Team
 * @version 1.0.0
 */
public enum RoleUtilisateur {

    /**
     * Gestionnaire - Droits en lecture et écriture uniquement
     */
    GESTIONNAIRE("Gestionnaire", 1, Set.of(
            Permission.READ_AFFAIRES,
            Permission.WRITE_AFFAIRES,
            Permission.READ_CONTREVENANTS,
            Permission.WRITE_CONTREVENANTS,
            Permission.READ_ENCAISSEMENTS,
            Permission.WRITE_ENCAISSEMENTS,
            Permission.READ_RAPPORTS,
            Permission.PRINT_RAPPORTS
    )),

    /**
     * Admin - Droits en lecture, écriture et modification
     */
    ADMIN("Administrateur", 2, Set.of(
            Permission.READ_AFFAIRES,
            Permission.WRITE_AFFAIRES,
            Permission.UPDATE_AFFAIRES,
            Permission.READ_CONTREVENANTS,
            Permission.WRITE_CONTREVENANTS,
            Permission.UPDATE_CONTREVENANTS,
            Permission.READ_AGENTS,
            Permission.WRITE_AGENTS,
            Permission.UPDATE_AGENTS,
            Permission.READ_ENCAISSEMENTS,
            Permission.WRITE_ENCAISSEMENTS,
            Permission.UPDATE_ENCAISSEMENTS,
            Permission.READ_REPARTITIONS,
            Permission.WRITE_REPARTITIONS,
            Permission.UPDATE_REPARTITIONS,
            Permission.READ_REFERENTIELS,
            Permission.WRITE_REFERENTIELS,
            Permission.UPDATE_REFERENTIELS,
            Permission.READ_RAPPORTS,
            Permission.WRITE_RAPPORTS,
            Permission.PRINT_RAPPORTS,
            Permission.EXPORT_DATA,
            Permission.MANAGE_USERS
    )),

    /**
     * Super Admin - Tous les droits (CRUD complet)
     */
    SUPER_ADMIN("Super Administrateur", 3, Set.of(
            Permission.READ_AFFAIRES,
            Permission.WRITE_AFFAIRES,
            Permission.UPDATE_AFFAIRES,
            Permission.DELETE_AFFAIRES,
            Permission.READ_CONTREVENANTS,
            Permission.WRITE_CONTREVENANTS,
            Permission.UPDATE_CONTREVENANTS,
            Permission.DELETE_CONTREVENANTS,
            Permission.READ_AGENTS,
            Permission.WRITE_AGENTS,
            Permission.UPDATE_AGENTS,
            Permission.DELETE_AGENTS,
            Permission.READ_ENCAISSEMENTS,
            Permission.WRITE_ENCAISSEMENTS,
            Permission.UPDATE_ENCAISSEMENTS,
            Permission.DELETE_ENCAISSEMENTS,
            Permission.READ_REPARTITIONS,
            Permission.WRITE_REPARTITIONS,
            Permission.UPDATE_REPARTITIONS,
            Permission.DELETE_REPARTITIONS,
            Permission.READ_REFERENTIELS,
            Permission.WRITE_REFERENTIELS,
            Permission.UPDATE_REFERENTIELS,
            Permission.DELETE_REFERENTIELS,
            Permission.READ_RAPPORTS,
            Permission.WRITE_RAPPORTS,
            Permission.PRINT_RAPPORTS,
            Permission.EXPORT_DATA,
            Permission.IMPORT_DATA,
            Permission.MANAGE_USERS,
            Permission.MANAGE_SYSTEM,
            Permission.MANAGE_DATABASE,
            Permission.AUDIT_LOGS
    ));

    private final String displayName;
    private final int niveau;
    private final Set<Permission> permissions;

    RoleUtilisateur(String displayName, int niveau, Set<Permission> permissions) {
        this.displayName = displayName;
        this.niveau = niveau;
        this.permissions = permissions;
    }

    /**
     * @return Nom d'affichage du rôle
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @return Niveau hiérarchique du rôle (plus élevé = plus de permissions)
     */
    public int getNiveau() {
        return niveau;
    }

    /**
     * @return Ensemble des permissions accordées à ce rôle
     */
    public Set<Permission> getPermissions() {
        return permissions;
    }

    /**
     * Vérifie si ce rôle possède une permission spécifique
     *
     * @param permission La permission à vérifier
     * @return true si le rôle possède cette permission
     */
    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    /**
     * Vérifie si ce rôle possède une permission spécifique par nom
     *
     * @param permissionName Le nom de la permission à vérifier
     * @return true si le rôle possède cette permission
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
     * Vérifie si ce rôle est administrateur (niveau 2 ou plus)
     *
     * @return true si le rôle est administrateur
     */
    public boolean isAdmin() {
        return niveau >= 2;
    }

    /**
     * Vérifie si ce rôle est super administrateur
     *
     * @return true si le rôle est super administrateur
     */
    public boolean isSuperAdmin() {
        return this == SUPER_ADMIN;
    }

    /**
     * Vérifie si ce rôle a un niveau supérieur ou égal à un autre rôle
     *
     * @param other L'autre rôle à comparer
     * @return true si ce rôle a un niveau supérieur ou égal
     */
    public boolean hasLevelOrHigher(RoleUtilisateur other) {
        return this.niveau >= other.niveau;
    }

    /**
     * Retourne la liste des rôles disponibles selon le rôle actuel
     * Un utilisateur ne peut créer que des comptes de niveau inférieur
     *
     * @param currentRole Le rôle de l'utilisateur actuel
     * @return Liste des rôles qu'il peut créer
     */
    public static List<RoleUtilisateur> getAvailableRoles(RoleUtilisateur currentRole) {
        return Arrays.stream(RoleUtilisateur.values())
                .filter(role -> role.niveau < currentRole.niveau)
                .toList();
    }

    /**
     * Trouve un rôle par son nom d'affichage
     *
     * @param displayName Le nom d'affichage à rechercher
     * @return Le rôle correspondant ou null si non trouvé
     */
    public static RoleUtilisateur findByDisplayName(String displayName) {
        return Arrays.stream(RoleUtilisateur.values())
                .filter(role -> role.displayName.equalsIgnoreCase(displayName))
                .findFirst()
                .orElse(null);
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Énumération des permissions spécifiques dans l'application
     */
    public enum Permission {
        // Permissions sur les affaires
        READ_AFFAIRES,
        WRITE_AFFAIRES,
        UPDATE_AFFAIRES,
        DELETE_AFFAIRES,

        // Permissions sur les contrevenants
        READ_CONTREVENANTS,
        WRITE_CONTREVENANTS,
        UPDATE_CONTREVENANTS,
        DELETE_CONTREVENANTS,

        // Permissions sur les agents
        READ_AGENTS,
        WRITE_AGENTS,
        UPDATE_AGENTS,
        DELETE_AGENTS,

        // Permissions sur les encaissements
        READ_ENCAISSEMENTS,
        WRITE_ENCAISSEMENTS,
        UPDATE_ENCAISSEMENTS,
        DELETE_ENCAISSEMENTS,

        // Permissions sur les répartitions
        READ_REPARTITIONS,
        WRITE_REPARTITIONS,
        UPDATE_REPARTITIONS,
        DELETE_REPARTITIONS,

        // Permissions sur les référentiels
        READ_REFERENTIELS,
        WRITE_REFERENTIELS,
        UPDATE_REFERENTIELS,
        DELETE_REFERENTIELS,

        // Permissions sur les rapports
        READ_RAPPORTS,
        WRITE_RAPPORTS,
        PRINT_RAPPORTS,

        // Permissions système
        EXPORT_DATA,
        IMPORT_DATA,
        MANAGE_USERS,
        MANAGE_SYSTEM,
        MANAGE_DATABASE,
        AUDIT_LOGS
    }
}