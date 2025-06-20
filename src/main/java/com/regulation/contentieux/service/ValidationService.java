package com.regulation.contentieux.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Service de validation des données
 * Centralise toutes les règles de validation métier
 */
public class ValidationService {

    private static final Logger logger = LoggerFactory.getLogger(ValidationService.class);

    // Patterns de validation
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\+241\\s?[0-9]{2}\\s?[0-9]{2}\\s?[0-9]{2}\\s?[0-9]{2}$");

    private static final Pattern CODE_PATTERN = Pattern.compile(
            "^[A-Z]{2,5}[0-9]{2,5}$");

    private static final Pattern NAME_PATTERN = Pattern.compile(
            "^[a-zA-ZÀ-ÿ\\s\\-']{2,100}$");

    private static final Pattern USERNAME_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_-]{3,50}$");

    /**
     * Valide une adresse email
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Valide un numéro de téléphone (format Gabon)
     */
    public boolean isValidPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }
        return PHONE_PATTERN.matcher(phone.trim()).matches();
    }

    /**
     * Valide un code (affaire, contrevenant, etc.)
     */
    public boolean isValidCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        return CODE_PATTERN.matcher(code.trim()).matches();
    }

    /**
     * Valide un nom de personne
     */
    public boolean isValidPersonName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        return NAME_PATTERN.matcher(name.trim()).matches();
    }

    /**
     * Valide un nom d'utilisateur
     */
    public boolean isValidUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        return USERNAME_PATTERN.matcher(username.trim()).matches();
    }

    /**
     * Valide un montant
     */
    public boolean isValidAmount(Double amount) {
        return amount != null && amount >= 0;
    }

    /**
     * Valide un montant positif strict
     */
    public boolean isValidPositiveAmount(Double amount) {
        return amount != null && amount > 0;
    }

    /**
     * Valide une date (non nulle et pas dans le futur)
     */
    public boolean isValidDate(LocalDate date) {
        if (date == null) {
            return false;
        }
        return !date.isAfter(LocalDate.now());
    }

    /**
     * Valide une plage de dates
     */
    public boolean isValidDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return false;
        }
        return !startDate.isAfter(endDate) && !endDate.isAfter(LocalDate.now());
    }

    /**
     * Valide une chaîne de caractères non vide
     */
    public boolean isValidString(String value, int minLength, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        String trimmed = value.trim();
        return trimmed.length() >= minLength && trimmed.length() <= maxLength;
    }

    /**
     * Valide un grade d'agent
     */
    public boolean isValidGrade(String grade) {
        if (grade == null || grade.trim().isEmpty()) {
            return false;
        }

        // Liste des grades valides
        String[] validGrades = {
                "Inspecteur", "Inspecteur Principal", "Inspecteur en Chef",
                "Contrôleur", "Contrôleur Principal", "Contrôleur en Chef",
                "Agent", "Agent Principal", "Agent en Chef"
        };

        for (String validGrade : validGrades) {
            if (validGrade.equalsIgnoreCase(grade.trim())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Valide un mot de passe
     */
    public boolean isValidPassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }

        // Au moins une lettre et un chiffre
        boolean hasLetter = password.matches(".*[a-zA-Z].*");
        boolean hasDigit = password.matches(".*[0-9].*");

        return hasLetter && hasDigit;
    }

    /**
     * Valide un pourcentage
     */
    public boolean isValidPercentage(Double percentage) {
        return percentage != null && percentage >= 0 && percentage <= 100;
    }

    /**
     * Valide un numéro de mandat
     */
    public boolean isValidNumeroMandat(String numeroMandat) {
        if (numeroMandat == null || numeroMandat.trim().isEmpty()) {
            return false;
        }

        // Format: lettres et chiffres, 5 à 20 caractères
        Pattern mandatPattern = Pattern.compile("^[A-Z0-9]{5,20}$");
        return mandatPattern.matcher(numeroMandat.trim().toUpperCase()).matches();
    }

    /**
     * Valide une référence bancaire
     */
    public boolean isValidReferenceBancaire(String reference) {
        if (reference == null || reference.trim().isEmpty()) {
            return false;
        }

        return isValidString(reference, 5, 50);
    }

    /**
     * Normalise une chaîne (supprime espaces superflus, met en forme)
     */
    public String normalizeString(String value) {
        if (value == null) {
            return null;
        }

        return value.trim().replaceAll("\\s+", " ");
    }

    /**
     * Normalise un code (majuscules, supprime espaces)
     */
    public String normalizeCode(String code) {
        if (code == null) {
            return null;
        }

        return code.trim().toUpperCase().replaceAll("\\s", "");
    }

    /**
     * Normalise un email (minuscules, supprime espaces)
     */
    public String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }

        return email.trim().toLowerCase();
    }

    /**
     * Normalise un nom de personne (première lettre en majuscule)
     */
    public String normalizePersonName(String name) {
        if (name == null) {
            return null;
        }

        String normalized = normalizeString(name);
        if (normalized.length() == 0) return name;

        // Capitaliser chaque mot
        String[] words = normalized.split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
                result.append(" ");
            }
        }

        return result.toString().trim();
    }

    /**
     * Valide un numéro RCCM (pour personnes morales)
     */
    public boolean isValidRCCM(String rccm) {
        if (rccm == null || rccm.trim().isEmpty()) {
            return false;
        }

        // Format RCCM: lettres et chiffres, 8 à 20 caractères
        Pattern rccmPattern = Pattern.compile("^[A-Z0-9\\-/]{8,20}$");
        return rccmPattern.matcher(rccm.trim().toUpperCase()).matches();
    }

    /**
     * Génère un nom de fichier sécurisé
     */
    public String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "document";
        }

        // Remplacer les caractères interdits
        return fileName.replaceAll("[^a-zA-Z0-9\\-_\\.]", "_")
                .replaceAll("_{2,}", "_")
                .toLowerCase();
    }
}