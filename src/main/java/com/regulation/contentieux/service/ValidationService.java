package com.regulation.contentieux.service;

import com.regulation.contentieux.model.*;
import java.math.BigDecimal;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service de validation des données métier
 * Centralise toutes les règles de validation de l'application
 */
public class ValidationService {

    private static final Logger logger = LoggerFactory.getLogger(ValidationService.class);

    // Patterns de validation
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^\\+?[0-9\\s\\-\\.\\(\\)]+$");

    private static final Pattern CODE_PATTERN =
            Pattern.compile("^[A-Z0-9\\-]+$");

    private static final Pattern REFERENCE_ENCAISSEMENT_PATTERN =
            Pattern.compile("^ENC-\\d{4}-\\d{5}$");

    private static final Pattern NUMERO_AFFAIRE_PATTERN =
            Pattern.compile("^AFF-\\d{4}-\\d{5}$");

    // Longueurs minimales et maximales
    private static final int MIN_LOGIN_LENGTH = 3;
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MIN_NAME_LENGTH = 2;
    private static final int MAX_CODE_LENGTH = 20;
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    /**
     * Valide un email
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return true; // Email optionnel
        }
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Valide un numéro de téléphone
     */
    public boolean isValidPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return true; // Téléphone optionnel
        }
        String cleaned = phone.replaceAll("\\s", "");
        return cleaned.length() >= 8 && PHONE_PATTERN.matcher(phone).matches();
    }

    /**
     * Valide un code (format majuscule avec tirets)
     */
    public boolean isValidCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        return code.length() <= MAX_CODE_LENGTH && CODE_PATTERN.matcher(code).matches();
    }

    /**
     * Valide une référence d'encaissement
     */
    public boolean isValidEncaissementReference(String reference) {
        if (reference == null || reference.trim().isEmpty()) {
            return false;
        }
        return REFERENCE_ENCAISSEMENT_PATTERN.matcher(reference).matches();
    }

    /**
     * Valide un numéro d'affaire
     */
    public boolean isValidNumeroAffaire(String numero) {
        if (numero == null || numero.trim().isEmpty()) {
            return false;
        }
        return NUMERO_AFFAIRE_PATTERN.matcher(numero).matches();
    }

    /**
     * Valide un montant
     */
    public boolean isValidMontant(BigDecimal montant) {
        if (montant == null) {
            return false;
        }
        return montant.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Valide un utilisateur
     */
    public boolean isValidUtilisateur(Utilisateur utilisateur) {
        if (utilisateur == null) {
            return false;
        }

        // Login obligatoire
        if (utilisateur.getLogin() == null ||
                utilisateur.getLogin().trim().length() < MIN_LOGIN_LENGTH) {
            logger.warn("Login invalide: {}", utilisateur.getLogin());
            return false;
        }

        // Nom obligatoire
        if (utilisateur.getNom() == null ||
                utilisateur.getNom().trim().length() < MIN_NAME_LENGTH) {
            logger.warn("Nom invalide: {}", utilisateur.getNom());
            return false;
        }

        // Email valide si fourni
        if (!isValidEmail(utilisateur.getEmail())) {
            logger.warn("Email invalide: {}", utilisateur.getEmail());
            return false;
        }

        // Rôle obligatoire
        if (utilisateur.getRole() == null) {
            logger.warn("Rôle manquant");
            return false;
        }

        return true;
    }

    /**
     * Valide un agent
     */
    public boolean isValidAgent(Agent agent) {
        if (agent == null) {
            return false;
        }

        // Code obligatoire et valide
        if (!isValidCode(agent.getCodeAgent())) {
            logger.warn("Code agent invalide: {}", agent.getCodeAgent());
            return false;
        }

        // Nom obligatoire
        if (agent.getNom() == null ||
                agent.getNom().trim().length() < MIN_NAME_LENGTH) {
            logger.warn("Nom agent invalide: {}", agent.getNom());
            return false;
        }

        // Prénom obligatoire
        if (agent.getPrenom() == null ||
                agent.getPrenom().trim().length() < MIN_NAME_LENGTH) {
            logger.warn("Prénom agent invalide: {}", agent.getPrenom());
            return false;
        }

        // Email valide si fourni
        if (!isValidEmail(agent.getEmail())) {
            logger.warn("Email agent invalide: {}", agent.getEmail());
            return false;
        }

        // Téléphone valide si fourni
        if (!isValidPhone(agent.getTelephone())) {
            logger.warn("Téléphone agent invalide: {}", agent.getTelephone());
            return false;
        }

        return true;
    }

    /**
     * Valide un contrevenant
     */
    public boolean isValidContrevenant(Contrevenant contrevenant) {
        if (contrevenant == null) {
            return false;
        }

        // Au moins nom ou raison sociale
        boolean hasNom = contrevenant.getNom() != null &&
                !contrevenant.getNom().trim().isEmpty();
        boolean hasRaisonSociale = contrevenant.getRaisonSociale() != null &&
                !contrevenant.getRaisonSociale().trim().isEmpty();

        if (!hasNom && !hasRaisonSociale) {
            logger.warn("Ni nom ni raison sociale fournis");
            return false;
        }

        // Type obligatoire
        if (contrevenant.getType() == null) {
            logger.warn("Type contrevenant manquant");
            return false;
        }

        // Email valide si fourni
        if (!isValidEmail(contrevenant.getEmail())) {
            logger.warn("Email contrevenant invalide: {}", contrevenant.getEmail());
            return false;
        }

        // Téléphone valide si fourni
        if (!isValidPhone(contrevenant.getTelephone())) {
            logger.warn("Téléphone contrevenant invalide: {}", contrevenant.getTelephone());
            return false;
        }

        return true;
    }

    /**
     * Valide une affaire
     */
    public boolean isValidAffaire(Affaire affaire) {
        if (affaire == null) {
            return false;
        }

        // Contrevenant obligatoire
        if (affaire.getContrevenant() == null) {
            logger.warn("Contrevenant manquant");
            return false;
        }

        // Date de constatation obligatoire
        if (affaire.getDateConstatation() == null) {
            logger.warn("Date de constatation manquante");
            return false;
        }

        // Lieu de constatation obligatoire
        if (affaire.getLieuConstatation() == null ||
                affaire.getLieuConstatation().trim().isEmpty()) {
            logger.warn("Lieu de constatation manquant");
            return false;
        }

        // Au moins une contravention
        if (affaire.getContraventions() == null ||
                affaire.getContraventions().isEmpty()) {
            logger.warn("Aucune contravention dans l'affaire");
            return false;
        }

        // Montant total cohérent
        if (!isValidMontant(affaire.getMontantTotal())) {
            logger.warn("Montant total invalide: {}", affaire.getMontantTotal());
            return false;
        }

        return true;
    }

    /**
     * Valide un encaissement
     */
    public boolean isValidEncaissement(Encaissement encaissement) {
        if (encaissement == null) {
            return false;
        }

        // Référence obligatoire et valide
        if (!isValidEncaissementReference(encaissement.getReference())) {
            logger.warn("Référence encaissement invalide: {}", encaissement.getReference());
            return false;
        }

        // Affaire obligatoire
        if (encaissement.getAffaire() == null) {
            logger.warn("Affaire manquante pour l'encaissement");
            return false;
        }

        // Montant valide
        if (!isValidMontant(encaissement.getMontantEncaisse())) {
            logger.warn("Montant encaissement invalide: {}", encaissement.getMontantEncaisse());
            return false;
        }

        // Mode de règlement obligatoire
        if (encaissement.getModeReglement() == null) {
            logger.warn("Mode de règlement manquant");
            return false;
        }

        // Date obligatoire
        if (encaissement.getDateEncaissement() == null) {
            logger.warn("Date encaissement manquante");
            return false;
        }

        return true;
    }

    /**
     * Valide un mot de passe
     */
    public boolean isValidPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return false;
        }

        // Au moins une majuscule, une minuscule et un chiffre
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);

        return hasUpper && hasLower && hasDigit;
    }

    /**
     * Valide une description
     */
    public boolean isValidDescription(String description) {
        if (description == null) {
            return true; // Description optionnelle
        }
        return description.length() <= MAX_DESCRIPTION_LENGTH;
    }
}