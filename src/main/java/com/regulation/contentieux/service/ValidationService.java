package com.regulation.contentieux.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Service de validation des données
 * Contient toutes les règles de validation métier
 */
public class ValidationService {

    private static final Logger logger = LoggerFactory.getLogger(ValidationService.class);

    // Expressions régulières pour validation
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^[+]?[0-9\\s\\-()]{8,20}$"
    );

    private static final Pattern CODE_PATTERN = Pattern.compile(
            "^[A-Z]{2}[0-9]{5}$"
    );

    private static final Pattern NUMERO_AFFAIRE_PATTERN = Pattern.compile(
            "^[0-9]{4}[0-9]{4}$"
    );

    /**
     * Valide une chaîne de caractères
     */
    public boolean isValidString(String value, int minLength, int maxLength) {
        if (value == null) {
            return false;
        }

        String trimmed = value.trim();
        return !trimmed.isEmpty() &&
                trimmed.length() >= minLength &&
                trimmed.length() <= maxLength;
    }

    /**
     * Valide un email
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }

        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Valide un numéro de téléphone
     */
    public boolean isValidPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }

        return PHONE_PATTERN.matcher(phone.trim()).matches();
    }

    /**
     * Valide un code (format général: 2 lettres + 5 chiffres)
     */
    public boolean isValidCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }

        return CODE_PATTERN.matcher(code.trim().toUpperCase()).matches();
    }

    /**
     * Valide un numéro d'affaire (format: YYMMNNNN)
     */
    public boolean isValidNumeroAffaire(String numeroAffaire) {
        if (numeroAffaire == null || numeroAffaire.trim().isEmpty()) {
            return false;
        }

        return NUMERO_AFFAIRE_PATTERN.matcher(numeroAffaire.trim()).matches();
    }

    /**
     * Valide un montant
     */
    public boolean isValidMontant(Double montant) {
        return montant != null && montant >= 0 && montant <= 999999999.99;
    }

    /**
     * Valide une date
     */
    public boolean isValidDate(LocalDate date) {
        if (date == null) {
            return false;
        }

        // Vérifier que la date n'est pas trop ancienne (plus de 50 ans)
        LocalDate minDate = LocalDate.now().minusYears(50);
        // Vérifier que la date n'est pas trop dans le futur (plus de 10 ans)
        LocalDate maxDate = LocalDate.now().plusYears(10);

        return !date.isBefore(minDate) && !date.isAfter(maxDate);
    }

    /**
     * Valide une date de naissance
     */
    public boolean isValidDateNaissance(LocalDate dateNaissance) {
        if (dateNaissance == null) {
            return false;
        }

        // Ne peut pas être dans le futur
        if (dateNaissance.isAfter(LocalDate.now())) {
            return false;
        }

        // Ne peut pas être trop ancienne (plus de 150 ans)
        LocalDate minDate = LocalDate.now().minusYears(150);
        return !dateNaissance.isBefore(minDate);
    }

    /**
     * Valide une date de création d'affaire
     */
    public boolean isValidDateCreationAffaire(LocalDate dateCreation) {
        if (dateCreation == null) {
            return false;
        }

        // Ne peut pas être dans le futur
        if (dateCreation.isAfter(LocalDate.now())) {
            return false;
        }

        // Ne peut pas être trop ancienne (plus de 20 ans)
        LocalDate minDate = LocalDate.now().minusYears(20);
        return !dateCreation.isBefore(minDate);
    }

    /**
     * Valide un nom de personne
     */
    public boolean isValidPersonName(String name) {
        if (!isValidString(name, 2, 100)) {
            return false;
        }

        // Vérifie que le nom ne contient que des lettres, espaces, tirets et apostrophes
        Pattern namePattern = Pattern.compile("^[a-zA-ZÀ-ÿ\\s\\-']+$");
        return namePattern.matcher(name.trim()).matches();
    }

    /**
     * Valide un grade d'agent
     */
    public boolean isValidGrade(String grade) {
        if (grade == null || grade.trim().isEmpty()) {
            return true; // Grade optionnel
        }

        return isValidString(grade, 2, 50);
    }

    /**
     * Valide une adresse
     */
    public boolean isValidAdresse(String adresse) {
        if (adresse == null || adresse.trim().isEmpty()) {
            return true; // Adresse optionnelle
        }

        return isValidString(adresse, 5, 500);
    }

    /**
     * Valide un nom d'utilisateur
     */
    public boolean isValidUsername(String username) {
        if (!isValidString(username, 3, 50)) {
            return false;
        }

        // Vérifie que le nom d'utilisateur ne contient que des lettres, chiffres et underscores
        Pattern usernamePattern = Pattern.compile("^[a-zA-Z0-9_]+$");
        return usernamePattern.matcher(username.trim()).matches();
    }

    /**
     * Valide un mot de passe
     */
    public boolean isValidPassword(String password) {
        if (password == null || password.length() < 6) {
            return false;
        }

        // Au moins 6 caractères, avec au moins une lettre et un chiffre
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
        if (normalized.isEmpty()) {
            return normalized;
        }

        // Met la première lettre de chaque mot en majuscule
        String[] words = normalized.toLowerCase().split("\\s+");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(" ");
            }

            String word = words[i];
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1));
                }
            }
        }

        return result.toString();
    }

    /**
     * Valide les règles métier pour une affaire
     */
    public void validateAffaireBusinessRules(LocalDate dateCreation, Double montantAmende) {
        // Règle 1: La date de création ne peut pas être dans le futur
        if (dateCreation != null && dateCreation.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La date de création ne peut pas être dans le futur");
        }

        // Règle 2: Le montant de l'amende doit être positif
        if (montantAmende != null && montantAmende <= 0) {
            throw new IllegalArgumentException("Le montant de l'amende doit être positif");
        }

        // Règle 3: Le montant ne peut pas dépasser 100 millions
        if (montantAmende != null && montantAmende > 100000000) {
            throw new IllegalArgumentException("Le montant de l'amende ne peut pas dépasser 100 millions");
        }
    }

    /**
     * Valide les règles métier pour un encaissement
     */
    public void validateEncaissementBusinessRules(Double montantEncaisse, Double montantAffaire) {
        // Règle 1: Le montant encaissé doit être positif
        if (montantEncaisse <= 0) {
            throw new IllegalArgumentException("Le montant encaissé doit être positif");
        }

        // Règle 2: Le montant encaissé ne peut pas dépasser le montant de l'affaire
        if (montantAffaire != null && montantEncaisse > montantAffaire) {
            throw new IllegalArgumentException("Le montant encaissé ne peut pas dépasser le montant de l'affaire");
        }
    }
}