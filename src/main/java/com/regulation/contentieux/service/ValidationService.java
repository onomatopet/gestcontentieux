package com.regulation.contentieux.service;

import com.regulation.contentieux.exception.ValidationException;
import com.regulation.contentieux.model.*;
import com.regulation.contentieux.model.enums.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Service de validation centralisé
 * Implémente toutes les règles de validation du cahier des charges
 */
public class ValidationService {

    private static final Logger logger = LoggerFactory.getLogger(ValidationService.class);

    // Patterns de validation
    private static final Pattern PATTERN_NUMERO_AFFAIRE = Pattern.compile("^\\d{9}$"); // YYMMNNNNN
    private static final Pattern PATTERN_NUMERO_ENCAISSEMENT = Pattern.compile("^\\d{4}R\\d{5}$"); // YYMMRNNNNN
    private static final Pattern PATTERN_NUMERO_MANDAT = Pattern.compile("^\\d{4}M\\d{4}$"); // YYMMM0001 // YYMM0001
    private static final Pattern PATTERN_EMAIL = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final Pattern PATTERN_TELEPHONE = Pattern.compile("^[0-9\\s\\-\\+\\.\\(\\)]+$");
    private static final Pattern PATTERN_CIN = Pattern.compile("^[A-Z0-9]{5,15}$");
    private static final Pattern PATTERN_CODE = Pattern.compile("^[A-Z0-9]{2,20}$"); // AJOUT pour validation des codes

    // Instance unique
    private static ValidationService instance;

    private ValidationService() {}

    public static synchronized ValidationService getInstance() {
        if (instance == null) {
            instance = new ValidationService();
        }
        return instance;
    }

    /**
     * Valide une affaire complète
     * @throws ValidationException si la validation échoue
     */
    public void validateAffaire(Affaire affaire) throws ValidationException {
        List<String> errors = new ArrayList<>();

        if (affaire == null) {
            throw new ValidationException("L'affaire ne peut pas être nulle");
        }

        // Validation du numéro d'affaire
        if (affaire.getNumeroAffaire() != null && !isValidNumeroAffaire(affaire.getNumeroAffaire())) {
            errors.add("Format du numéro d'affaire invalide (attendu: YYMMNNNNN)");
        }

        // Validation du contrevenant
        if (affaire.getContrevenant() == null) {
            errors.add("Le contrevenant est obligatoire");
        } else {
            validateContrevenant(affaire.getContrevenant(), errors);
        }

        // Validation des contraventions
        if (affaire.getContraventions() == null || affaire.getContraventions().isEmpty()) {
            errors.add("Au moins une contravention est obligatoire");
        }

        // Validation du montant
        if (affaire.getMontantAmendeTotal() == null || affaire.getMontantAmendeTotal().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Le montant de l'amende doit être supérieur à zéro");
        }

        // Validation de la date
        if (affaire.getDateCreation() != null && affaire.getDateCreation().isAfter(LocalDate.now())) {
            errors.add("La date de création ne peut pas être dans le futur");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Erreurs de validation de l'affaire", errors);
        }
    }

    /**
     * Valide un encaissement
     * Règle métier : montant encaissé ≤ montant amende
     */
    public void validateEncaissement(Encaissement encaissement, Affaire affaire) throws ValidationException {
        List<String> errors = new ArrayList<>();

        if (encaissement == null) {
            throw new ValidationException("L'encaissement ne peut pas être nul");
        }

        // Validation de la référence
        if (encaissement.getReference() != null && !isValidNumeroEncaissement(encaissement.getReference())) {
            errors.add("Format de la référence d'encaissement invalide (attendu: YYMMRNNNNN)");
        }

        // Validation du montant
        if (encaissement.getMontantEncaisse() == null || encaissement.getMontantEncaisse().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Le montant encaissé doit être supérieur à zéro");
        }

        // RÈGLE MÉTIER : montant encaissé ≤ montant amende
        if (affaire != null && encaissement.getMontantEncaisse() != null) {
            BigDecimal soldeRestant = affaire.getSoldeRestant();
            if (encaissement.getMontantEncaisse().compareTo(soldeRestant) > 0) {
                errors.add(String.format("Le montant encaissé (%s) ne peut pas dépasser le solde restant (%s)",
                        encaissement.getMontantEncaisse(), soldeRestant));
            }
        }

        // Validation de la date
        if (encaissement.getDateEncaissement() == null) {
            errors.add("La date d'encaissement est obligatoire");
        } else if (encaissement.getDateEncaissement().isAfter(LocalDate.now())) {
            errors.add("La date d'encaissement ne peut pas être dans le futur");
        }

        // Validation du mode de règlement
        if (encaissement.getModeReglement() == null) {
            errors.add("Le mode de règlement est obligatoire");
        } else if (encaissement.getModeReglement() == ModeReglement.CHEQUE) {
            // Si chèque, vérifier les infos bancaires
            if (encaissement.getBanque() == null) {
                errors.add("La banque est obligatoire pour un paiement par chèque");
            }
            if (encaissement.getNumeroPiece() == null || encaissement.getNumeroPiece().trim().isEmpty()) {
                errors.add("Le numéro de chèque est obligatoire");
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Erreurs de validation de l'encaissement", errors);
        }
    }

    /**
     * Valide les acteurs d'une affaire
     * Règle métier : au moins un saisissant obligatoire
     */
    public void validateActeurs(List<AffaireActeur> acteurs) throws ValidationException {
        if (acteurs == null || acteurs.isEmpty()) {
            throw new ValidationException("Au moins un acteur est obligatoire");
        }

        // Vérifier qu'il y a au moins un saisissant
        // Le rôle est stocké comme String dans AffaireActeur
        boolean hasSaisissant = acteurs.stream()
                .anyMatch(a -> "SAISISSANT".equals(a.getRoleSurAffaire()));

        if (!hasSaisissant) {
            throw new ValidationException("Au moins un agent saisissant est obligatoire");
        }
    }

    /**
     * Valide la cohérence des dates avec le mandat
     */
    public void validateDateAvecMandat(LocalDate date, String numeroMandat) throws ValidationException {
        if (date == null || numeroMandat == null) {
            return;
        }

        // Extraire YYMM du mandat et de la date
        String yymmMandat = numeroMandat.substring(0, 4);
        String yymmDate = date.format(DateTimeFormatter.ofPattern("yyMM"));

        if (!yymmMandat.equals(yymmDate)) {
            throw new ValidationException(
                    String.format("La date %s n'est pas dans le mandat %s",
                            date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                            numeroMandat)
            );
        }
    }

    /**
     * Valide un contrevenant
     */
    private void validateContrevenant(Contrevenant contrevenant, List<String> errors) {
        if (contrevenant.getType() == null && contrevenant.getTypePersonne() == null) {
            errors.add("Le type de contrevenant est obligatoire");
            return;
        }

        if (contrevenant.getType() == TypeContrevenant.PERSONNE_PHYSIQUE ||
                "PHYSIQUE".equals(contrevenant.getTypePersonne())) {

            if (isBlank(contrevenant.getNom())) {
                errors.add("Le nom du contrevenant est obligatoire");
            }

            if (isBlank(contrevenant.getCin())) {
                errors.add("Le CIN est obligatoire pour une personne physique");
            } else if (!isValidCIN(contrevenant.getCin())) {
                errors.add("Format du CIN invalide");
            }

        } else if (contrevenant.getType() == TypeContrevenant.PERSONNE_MORALE ||
                "MORALE".equals(contrevenant.getTypePersonne())) {

            if (isBlank(contrevenant.getRaisonSociale())) {
                errors.add("La raison sociale est obligatoire pour une personne morale");
            }

            if (isBlank(contrevenant.getNumeroRegistreCommerce())) {
                errors.add("Le numéro de registre de commerce est obligatoire");
            }
        }

        // Validation des coordonnées
        if (!isBlank(contrevenant.getEmail()) && !isValidEmail(contrevenant.getEmail())) {
            errors.add("Format d'email invalide");
        }

        if (!isBlank(contrevenant.getTelephone()) && !isValidTelephone(contrevenant.getTelephone())) {
            errors.add("Format de téléphone invalide");
        }
    }

    /**
     * Valide un agent
     */
    public void validateAgent(Agent agent) throws ValidationException {
        List<String> errors = new ArrayList<>();

        if (agent == null) {
            throw new ValidationException("L'agent ne peut pas être nul");
        }

        if (isBlank(agent.getCodeAgent())) {
            errors.add("Le code agent est obligatoire");
        }

        if (isBlank(agent.getNom())) {
            errors.add("Le nom de l'agent est obligatoire");
        }

        if (isBlank(agent.getPrenom())) {
            errors.add("Le prénom de l'agent est obligatoire");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Erreurs de validation de l'agent", errors);
        }
    }

    /**
     * Valide un utilisateur
     */
    public void validateUtilisateur(Utilisateur utilisateur) throws ValidationException {
        List<String> errors = new ArrayList<>();

        if (utilisateur == null) {
            throw new ValidationException("L'utilisateur ne peut pas être nul");
        }

        if (isBlank(utilisateur.getLogin())) {
            errors.add("Le login est obligatoire");
        } else if (utilisateur.getLogin().length() < 3) {
            errors.add("Le login doit contenir au moins 3 caractères");
        }

        if (utilisateur.getRole() == null) {
            errors.add("Le rôle est obligatoire");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Erreurs de validation de l'utilisateur", errors);
        }
    }

    /**
     * Valide un mot de passe
     */
    public void validatePassword(String password) throws ValidationException {
        if (password == null || password.length() < 6) {
            throw new ValidationException("Le mot de passe doit contenir au moins 6 caractères");
        }

        // On pourrait ajouter d'autres règles (majuscule, chiffre, etc.)
    }

    // ========== Méthodes de validation unitaires ==========

    public boolean isValidNumeroAffaire(String numero) {
        if (numero == null) return false;

        // Format YYMMNNNNN
        if (!PATTERN_NUMERO_AFFAIRE.matcher(numero).matches()) {
            return false;
        }

        // Vérifier que le mois est valide
        try {
            int month = Integer.parseInt(numero.substring(2, 4));
            return month >= 1 && month <= 12;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public boolean isValidNumeroEncaissement(String numero) {
        if (numero == null) return false;

        // Format YYMMRNNNNN
        if (!PATTERN_NUMERO_ENCAISSEMENT.matcher(numero).matches()) {
            return false;
        }

        // Vérifier que le mois est valide
        try {
            int month = Integer.parseInt(numero.substring(2, 4));
            return month >= 1 && month <= 12;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Normalise un texte générique (utilisé pour les noms de services, bureaux, etc.)
     * - Supprime les espaces en début/fin
     * - Remplace les espaces multiples par un seul
     * - Conserve la casse originale (contrairement à normalizePersonName)
     *
     * @param text Le texte à normaliser
     * @return Le texte normalisé
     */
    public String normalizeText(String text) {
        if (text == null) {
            return null;
        }

        // Supprimer les espaces en début/fin
        String normalized = text.trim();

        // Remplacer les espaces multiples par un seul
        normalized = normalized.replaceAll("\\s+", " ");

        return normalized;
    }

    /**
     * Formate un numéro de téléphone selon les standards
     * - Supprime tous les caractères non numériques sauf le +
     * - Ajoute des espaces pour la lisibilité
     *
     * @param phoneNumber Le numéro à formater
     * @return Le numéro formaté
     */
    public String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return phoneNumber;
        }

        // Garder uniquement les chiffres et le +
        String cleaned = phoneNumber.replaceAll("[^0-9+]", "");

        // Si le numéro commence par +, le préserver
        boolean hasPlus = cleaned.startsWith("+");
        if (hasPlus) {
            cleaned = cleaned.substring(1);
        }

        // Formater selon la longueur
        String formatted = cleaned;

        // Format gabonais : +241 XX XX XX XX
        if (cleaned.startsWith("241") && cleaned.length() == 11) {
            formatted = String.format("%s %s %s %s %s",
                    cleaned.substring(0, 3),
                    cleaned.substring(3, 5),
                    cleaned.substring(5, 7),
                    cleaned.substring(7, 9),
                    cleaned.substring(9, 11)
            );
        }
        // Format local gabonais : 0X XX XX XX XX
        else if (cleaned.startsWith("0") && cleaned.length() == 10) {
            formatted = String.format("%s %s %s %s %s",
                    cleaned.substring(0, 2),
                    cleaned.substring(2, 4),
                    cleaned.substring(4, 6),
                    cleaned.substring(6, 8),
                    cleaned.substring(8, 10)
            );
        }
        // Format générique pour 10 chiffres
        else if (cleaned.length() == 10) {
            formatted = String.format("%s %s %s %s",
                    cleaned.substring(0, 3),
                    cleaned.substring(3, 6),
                    cleaned.substring(6, 8),
                    cleaned.substring(8, 10)
            );
        }
        // Format générique pour 9 chiffres
        else if (cleaned.length() == 9) {
            formatted = String.format("%s %s %s",
                    cleaned.substring(0, 3),
                    cleaned.substring(3, 6),
                    cleaned.substring(6, 9)
            );
        }

        // Rajouter le + si nécessaire
        if (hasPlus) {
            formatted = "+" + formatted;
        }

        return formatted;
    }

    public boolean isValidNumeroMandat(String numero) {
        if (numero == null || numero.trim().isEmpty()) {
            return false;
        }

        // Format YYMMM0001 (avec M obligatoire)
        if (!PATTERN_NUMERO_MANDAT.matcher(numero).matches()) {
            return false;
        }

        // Vérifier que le mois est valide (01-12)
        try {
            String moisStr = numero.substring(2, 4);
            int mois = Integer.parseInt(moisStr);
            if (mois < 1 || mois > 12) {
                return false;
            }

            // Vérifier que le M est bien présent à la position 4
            if (numero.charAt(4) != 'M') {
                return false;
            }

            // Vérifier que le numéro séquentiel est valide (0001-9999)
            String sequenceStr = numero.substring(5);
            int sequence = Integer.parseInt(sequenceStr);
            return sequence >= 1 && sequence <= 9999;

        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            return false;
        }
    }

    public boolean isValidEmail(String email) {
        return email != null && PATTERN_EMAIL.matcher(email).matches();
    }

    public boolean isValidTelephone(String telephone) {
        return telephone != null && PATTERN_TELEPHONE.matcher(telephone).matches();
    }

    /**
     * AJOUT : Alias isValidPhone pour compatibilité avec ContrevenantService
     */
    public boolean isValidPhone(String telephone) {
        return isValidTelephone(telephone);
    }

    public boolean isValidCIN(String cin) {
        return cin != null && PATTERN_CIN.matcher(cin.toUpperCase()).matches();
    }

    public boolean isValidMontant(BigDecimal montant) {
        return montant != null && montant.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isValidDate(LocalDate date) {
        return date != null && !date.isAfter(LocalDate.now());
    }

    /**
     * AJOUT : Valide un code générique (pour les codes agent, contrevenant, etc.)
     * Utilisé par ContrevenantService
     */
    public boolean isValidCode(String code) {
        return code != null && PATTERN_CODE.matcher(code.toUpperCase()).matches();
    }

    /**
     * AJOUT : Valide une chaîne de caractères avec longueur min/max
     * Utilisé par ContraventionService et ContrevenantService
     */
    public boolean isValidString(String str, int minLength, int maxLength) {
        if (str == null || str.trim().isEmpty()) {
            return minLength == 0; // Retourne true si la chaîne peut être vide
        }
        String trimmed = str.trim();
        return trimmed.length() >= minLength && trimmed.length() <= maxLength;
    }

    /**
     * AJOUT : Valide un nom de personne (nom ou prénom)
     * Utilisé par AgentService
     */
    public boolean isValidPersonName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        String trimmed = name.trim();
        // Accepte les lettres, espaces, tirets et apostrophes
        return trimmed.length() >= 2 && trimmed.length() <= 100 &&
                trimmed.matches("^[a-zA-ZÀ-ÿ\\s\\-']+$");
    }

    /**
     * AJOUT : Normalise un nom de personne (capitalisation correcte)
     * Utilisé par AgentService
     */
    public String normalizePersonName(String name) {
        if (name == null) {
            return null;
        }

        // Supprimer les espaces en début/fin
        String normalized = name.trim();

        // Remplacer les espaces multiples par un seul
        normalized = normalized.replaceAll("\\s+", " ");

        // Capitaliser la première lettre de chaque mot
        String[] words = normalized.split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                if (result.length() > 0) {
                    result.append(" ");
                }
                // Première lettre en majuscule, le reste en minuscule
                result.append(word.substring(0, 1).toUpperCase());
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
            }
        }

        return result.toString();
    }

    /**
     * AJOUT : Valide un grade d'agent
     * Utilisé par AgentService
     */
    public boolean isValidGrade(String grade) {
        if (grade == null || grade.trim().isEmpty()) {
            return true; // Le grade est optionnel
        }

        // Liste des grades valides
        String[] gradesValides = {
                "Inspecteur Principal",
                "Inspecteur",
                "Contrôleur Principal",
                "Contrôleur",
                "Agent Principal",
                "Agent"
        };

        for (String gradeValide : gradesValides) {
            if (gradeValide.equalsIgnoreCase(grade.trim())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Vérifie l'unicité d'un numéro
     * À implémenter avec les DAOs correspondants
     */
    public boolean isUniqueNumeroAffaire(String numero) {
        // TODO: Vérifier en base
        return true;
    }

    public boolean isUniqueNumeroEncaissement(String numero) {
        // TODO: Vérifier en base
        return true;
    }

    public boolean isUniqueNumeroMandat(String numero) {
        // TODO: Vérifier en base
        return true;
    }

    // ========== Méthodes utilitaires ==========

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Résultat de validation avec détails
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final Map<String, String> fieldErrors;

        public ValidationResult() {
            this.valid = true;
            this.errors = new ArrayList<>();
            this.fieldErrors = new HashMap<>();
        }

        public ValidationResult(List<String> errors) {
            this.valid = errors.isEmpty();
            this.errors = errors;
            this.fieldErrors = new HashMap<>();
        }

        public boolean isValid() {
            return valid;
        }

        public boolean hasErrors() {
            return !errors.isEmpty() || !fieldErrors.isEmpty();
        }

        public List<String> getErrors() {
            return errors;
        }

        public Map<String, String> getFieldErrors() {
            return fieldErrors;
        }

        public void addError(String error) {
            errors.add(error);
        }

        public void addFieldError(String field, String error) {
            fieldErrors.put(field, error);
        }

        public String getErrorMessage() {
            if (errors.isEmpty() && fieldErrors.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();

            if (!errors.isEmpty()) {
                sb.append(String.join("\n", errors));
            }

            if (!fieldErrors.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                fieldErrors.forEach((field, error) ->
                        sb.append(field).append(" : ").append(error).append("\n")
                );
            }

            return sb.toString();
        }
    }
}