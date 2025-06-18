package com.regulation.contentieux.service;

import com.regulation.contentieux.dao.UtilisateurDAO;
import com.regulation.contentieux.model.Utilisateur;
import com.regulation.contentieux.model.enums.RoleUtilisateur;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Service d'authentification et gestion des utilisateurs
 */
public class AuthenticationService {
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);
    private static AuthenticationService instance;

    private final UtilisateurDAO utilisateurDAO;
    private Utilisateur currentUser;

    private AuthenticationService() {
        this.utilisateurDAO = new UtilisateurDAO();
    }

    public static AuthenticationService getInstance() {
        if (instance == null) {
            instance = new AuthenticationService();
        }
        return instance;
    }

    /**
     * Authentifie un utilisateur
     */
    public AuthenticationResult authenticate(String username, String password) {
        try {
            logger.info("Tentative de connexion pour: {}", username);

            // Vérifications de base
            if (username == null || username.trim().isEmpty()) {
                return new AuthenticationResult(false, "Nom d'utilisateur requis", null);
            }

            if (password == null || password.trim().isEmpty()) {
                return new AuthenticationResult(false, "Mot de passe requis", null);
            }

            // Recherche de l'utilisateur
            Optional<Utilisateur> userOptional = utilisateurDAO.findByUsername(username.trim());

            if (userOptional.isEmpty()) {
                logger.warn("Tentative de connexion avec un utilisateur inexistant: {}", username);
                return new AuthenticationResult(false, "Utilisateur non trouvé", null);
            }

            Utilisateur user = userOptional.get();

            // Vérification du statut actif
            if (!user.isActif()) {
                logger.warn("Tentative de connexion avec un compte désactivé: {}", username);
                return new AuthenticationResult(false, "Compte désactivé", null);
            }

            // Vérification du mot de passe
            String hashedPassword = hashPassword(password);
            if (!hashedPassword.equals(user.getPasswordHash())) {
                logger.warn("Mot de passe incorrect pour: {}", username);
                return new AuthenticationResult(false, "Mot de passe incorrect", null);
            }

            // Connexion réussie
            currentUser = user;
            utilisateurDAO.updateLastLogin(user.getId());

            logger.info("Connexion réussie pour: {} ({})", username, user.getRole().getDisplayName());
            return new AuthenticationResult(true, "Connexion réussie", user);

        } catch (Exception e) {
            logger.error("Erreur lors de l'authentification de: " + username, e);
            return new AuthenticationResult(false, "Erreur système lors de la connexion", null);
        }
    }

    /**
     * Déconnecte l'utilisateur actuel
     */
    public void logout() {
        if (currentUser != null) {
            logger.info("Déconnexion de: {}", currentUser.getUsername());
            currentUser = null;
        }
    }

    /**
     * Retourne l'utilisateur actuellement connecté
     */
    public Utilisateur getCurrentUser() {
        return currentUser;
    }

    /**
     * Vérifie si un utilisateur est connecté
     */
    public boolean isAuthenticated() {
        return currentUser != null;
    }

    /**
     * Vérifie si l'utilisateur actuel a une permission
     */
    public boolean hasPermission(RoleUtilisateur.Permission permission) {
        return currentUser != null && currentUser.hasPermission(permission);
    }

    /**
     * Vérifie si l'utilisateur actuel est admin
     */
    public boolean isCurrentUserAdmin() {
        return currentUser != null && currentUser.isAdmin();
    }

    /**
     * Vérifie si l'utilisateur actuel est super admin
     */
    public boolean isCurrentUserSuperAdmin() {
        return currentUser != null && currentUser.isSuperAdmin();
    }

    /**
     * Crée un nouvel utilisateur
     */
    public AuthenticationResult createUser(String username, String password, String nomComplet, RoleUtilisateur role) {
        try {
            // Vérifications de base
            if (username == null || username.trim().isEmpty()) {
                return new AuthenticationResult(false, "Nom d'utilisateur requis", null);
            }

            if (password == null || password.length() < 6) {
                return new AuthenticationResult(false, "Mot de passe trop court (minimum 6 caractères)", null);
            }

            if (nomComplet == null || nomComplet.trim().isEmpty()) {
                return new AuthenticationResult(false, "Nom complet requis", null);
            }

            // Vérification d'unicité
            if (utilisateurDAO.existsByUsername(username.trim())) {
                return new AuthenticationResult(false, "Ce nom d'utilisateur existe déjà", null);
            }

            // Création de l'utilisateur
            Utilisateur newUser = new Utilisateur();
            newUser.setUsername(username.trim());
            newUser.setPasswordHash(hashPassword(password));
            newUser.setNomComplet(nomComplet.trim());
            newUser.setRole(role);

            Utilisateur savedUser = utilisateurDAO.save(newUser);

            logger.info("Nouvel utilisateur créé: {} ({})", username, role.getDisplayName());
            return new AuthenticationResult(true, "Utilisateur créé avec succès", savedUser);

        } catch (Exception e) {
            logger.error("Erreur lors de la création de l'utilisateur: " + username, e);
            return new AuthenticationResult(false, "Erreur lors de la création de l'utilisateur", null);
        }
    }

    /**
     * Change le mot de passe de l'utilisateur actuel
     */
    public AuthenticationResult changePassword(String oldPassword, String newPassword) {
        if (currentUser == null) {
            return new AuthenticationResult(false, "Aucun utilisateur connecté", null);
        }

        try {
            // Vérification de l'ancien mot de passe
            String hashedOldPassword = hashPassword(oldPassword);
            if (!hashedOldPassword.equals(currentUser.getPasswordHash())) {
                return new AuthenticationResult(false, "Ancien mot de passe incorrect", null);
            }

            // Validation du nouveau mot de passe
            if (newPassword == null || newPassword.length() < 6) {
                return new AuthenticationResult(false, "Nouveau mot de passe trop court (minimum 6 caractères)", null);
            }

            // Mise à jour
            currentUser.setPasswordHash(hashPassword(newPassword));
            utilisateurDAO.update(currentUser);

            logger.info("Mot de passe changé pour: {}", currentUser.getUsername());
            return new AuthenticationResult(true, "Mot de passe changé avec succès", currentUser);

        } catch (Exception e) {
            logger.error("Erreur lors du changement de mot de passe", e);
            return new AuthenticationResult(false, "Erreur lors du changement de mot de passe", null);
        }
    }

    /**
     * Hache un mot de passe avec SHA-256 (simple pour l'instant)
     * TODO: Remplacer par BCrypt plus tard
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algorithme SHA-256 non disponible", e);
        }
    }

    /**
     * Classe pour encapsuler le résultat d'une authentification
     */
    public static class AuthenticationResult {
        private final boolean success;
        private final String message;
        private final Utilisateur user;

        public AuthenticationResult(boolean success, String message, Utilisateur user) {
            this.success = success;
            this.message = message;
            this.user = user;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Utilisateur getUser() { return user; }
    }
}