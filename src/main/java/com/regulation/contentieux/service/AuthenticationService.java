package com.regulation.contentieux.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.regulation.contentieux.dao.UtilisateurDAO;
import com.regulation.contentieux.model.Utilisateur;
import com.regulation.contentieux.model.enums.RoleUtilisateur;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service d'authentification et de gestion des sessions
 * Pattern Singleton pour garantir une instance unique
 */
public class AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);
    private static AuthenticationService instance;

    private final UtilisateurDAO utilisateurDAO;
    private Utilisateur currentUser;

    private AuthenticationService() {
        this.utilisateurDAO = new UtilisateurDAO();
    }

    /**
     * Obtient l'instance unique du service
     */
    public static synchronized AuthenticationService getInstance() {
        if (instance == null) {
            instance = new AuthenticationService();
        }
        return instance;
    }

    /**
     * Authentifie un utilisateur
     */
    public boolean authenticate(String login, String password) {
        try {
            logger.info("Tentative de connexion pour l'utilisateur: {}", login);

            Optional<Utilisateur> optUser = utilisateurDAO.findByLogin(login);

            if (optUser.isEmpty()) {
                logger.warn("Utilisateur non trouvé: {}", login);
                return false;
            }

            Utilisateur utilisateur = optUser.get();

            // Vérifier si l'utilisateur est actif
            if (!utilisateur.isActif()) {
                logger.warn("Tentative de connexion avec un compte désactivé: {}", login);
                return false;
            }

            // Vérifier le mot de passe
            BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(),
                    utilisateur.getMotDePasse());

            if (result.verified) {
                // Mise à jour de la dernière connexion
                utilisateur.setDerniereConnexion(LocalDateTime.now());
                utilisateurDAO.update(utilisateur);

                // Stocker l'utilisateur courant
                this.currentUser = utilisateur;

                logger.info("Connexion réussie pour: {}", login);
                return true;
            } else {
                logger.warn("Mot de passe incorrect pour: {}", login);
                return false;
            }

        } catch (Exception e) {
            logger.error("Erreur lors de l'authentification", e);
            return false;
        }
    }

    /**
     * Déconnecte l'utilisateur courant
     */
    public void logout() {
        if (currentUser != null) {
            logger.info("Déconnexion de l'utilisateur: {}", currentUser.getLogin());
            currentUser = null;
        }
    }

    /**
     * Retourne l'utilisateur connecté
     */
    public Utilisateur getCurrentUser() {
        return currentUser;
    }

    /**
     * Retourne le nom d'utilisateur de l'utilisateur connecté
     */
    public String getCurrentUsername() {
        if (currentUser != null) {
            return currentUser.getUsername();
        }
        return "SYSTEM";
    }

    /**
     * Vérifie si un utilisateur est connecté
     */
    public boolean isAuthenticated() {
        return currentUser != null;
    }

    /**
     * Vérifie si l'utilisateur courant a une permission
     */
    public boolean hasPermission(String permission) {
        if (currentUser == null) {
            return false;
        }
        return currentUser.hasPermission(permission);
    }

    /**
     * Vérifie si l'utilisateur courant a une permission
     */
    public boolean hasPermission(RoleUtilisateur.Permission permission) {
        if (currentUser == null) {
            return false;
        }
        return currentUser.getRole().hasPermission(permission);
    }

    /**
     * Change le mot de passe de l'utilisateur
     */
    public boolean changePassword(String oldPassword, String newPassword) {
        if (currentUser == null) {
            return false;
        }

        try {
            // Vérifier l'ancien mot de passe
            BCrypt.Result result = BCrypt.verifyer().verify(oldPassword.toCharArray(),
                    currentUser.getMotDePasse());

            if (!result.verified) {
                logger.warn("Échec du changement de mot de passe - ancien mot de passe incorrect");
                return false;
            }

            // Hasher le nouveau mot de passe
            String hashedPassword = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray());
            currentUser.setMotDePasse(hashedPassword);

            // Sauvegarder
            utilisateurDAO.update(currentUser);

            logger.info("Mot de passe changé avec succès pour: {}", currentUser.getLogin());
            return true;

        } catch (Exception e) {
            logger.error("Erreur lors du changement de mot de passe", e);
            return false;
        }
    }

    /**
     * Crée un nouvel utilisateur
     */
    public Utilisateur createUser(String login, String password, String nom, String prenom,
                                  String email, RoleUtilisateur role) {
        try {
            // Vérifier que le login n'existe pas déjà
            if (utilisateurDAO.existsByLogin(login)) {
                throw new IllegalArgumentException("Ce login existe déjà");
            }

            // Créer l'utilisateur
            Utilisateur newUser = new Utilisateur();
            newUser.setLogin(login);
            newUser.setMotDePasse(BCrypt.withDefaults().hashToString(12, password.toCharArray()));
            newUser.setNom(nom);
            newUser.setPrenom(prenom);
            newUser.setEmail(email);
            newUser.setRole(role);
            newUser.setActif(true);
            newUser.setCreatedBy(getCurrentUsername());

            // Sauvegarder
            Utilisateur savedUser = utilisateurDAO.save(newUser);
            logger.info("Utilisateur créé avec succès: {}", login);

            return savedUser;

        } catch (Exception e) {
            logger.error("Erreur lors de la création de l'utilisateur", e);
            throw new RuntimeException("Impossible de créer l'utilisateur: " + e.getMessage());
        }
    }

    /**
     * Vérifie si un login existe déjà
     */
    public boolean loginExists(String login) {
        return utilisateurDAO.existsByLogin(login);
    }

    /**
     * Réinitialise le mot de passe d'un utilisateur
     */
    public boolean resetPassword(String login, String newPassword) {
        try {
            Optional<Utilisateur> optUser = utilisateurDAO.findByLogin(login);

            if (optUser.isEmpty()) {
                logger.warn("Tentative de réinitialisation pour un utilisateur inexistant: {}", login);
                return false;
            }

            Utilisateur utilisateur = optUser.get();
            String hashedPassword = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray());
            utilisateur.setMotDePasse(hashedPassword);
            utilisateur.setUpdatedBy(getCurrentUsername());
            utilisateur.setUpdatedAt(LocalDateTime.now());

            utilisateurDAO.update(utilisateur);

            logger.info("Mot de passe réinitialisé pour: {}", login);
            return true;

        } catch (Exception e) {
            logger.error("Erreur lors de la réinitialisation du mot de passe", e);
            return false;
        }
    }

    /**
     * Classe interne pour le résultat d'authentification
     */
    public static class AuthenticationResult {
        private final boolean success;
        private final String message;
        private final Utilisateur user;

        private AuthenticationResult(boolean success, String message, Utilisateur user) {
            this.success = success;
            this.message = message;
            this.user = user;
        }

        public static AuthenticationResult success(Utilisateur user) {
            return new AuthenticationResult(true, "Authentification réussie", user);
        }

        public static AuthenticationResult failure(String message) {
            return new AuthenticationResult(false, message, null);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Utilisateur getUser() { return user; }
    }

    /**
     * Authentifie un utilisateur avec résultat détaillé
     */
    public AuthenticationResult authenticateWithResult(String login, String password) {
        try {
            logger.info("Tentative de connexion pour l'utilisateur: {}", login);

            Optional<Utilisateur> optUser = utilisateurDAO.findByLogin(login);

            if (optUser.isEmpty()) {
                logger.warn("Utilisateur non trouvé: {}", login);
                return AuthenticationResult.failure("Identifiants incorrects");
            }

            Utilisateur utilisateur = optUser.get();

            if (!utilisateur.isActif()) {
                logger.warn("Tentative de connexion avec un compte désactivé: {}", login);
                return AuthenticationResult.failure("Compte désactivé");
            }

            BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(),
                    utilisateur.getMotDePasse());

            if (result.verified) {
                utilisateur.setDerniereConnexion(LocalDateTime.now());
                utilisateurDAO.update(utilisateur);
                this.currentUser = utilisateur;

                logger.info("Connexion réussie pour: {}", login);
                return AuthenticationResult.success(utilisateur);
            } else {
                logger.warn("Mot de passe incorrect pour: {}", login);
                return AuthenticationResult.failure("Identifiants incorrects");
            }

        } catch (Exception e) {
            logger.error("Erreur lors de l'authentification", e);
            return AuthenticationResult.failure("Erreur technique");
        }
    }
}