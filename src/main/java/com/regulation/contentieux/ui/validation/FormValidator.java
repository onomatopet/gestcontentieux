package com.regulation.contentieux.ui.validation;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * ENRICHISSEMENT UI - Phase 3 Partie 2
 * Système de validation standardisé selon le cahier des charges
 * "Messages d'erreur contextuels"
 */
public class FormValidator {

    private static final Logger logger = LoggerFactory.getLogger(FormValidator.class);

    // Patterns de validation réutilisables
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^[0-9\\s\\-\\+\\.\\(\\)]{8,15}$"
    );
    private static final Pattern CIN_PATTERN = Pattern.compile(
            "^[A-Z0-9]{5,15}$"
    );

    // État du validateur
    private final List<ValidationRule> rules = new ArrayList<>();
    private final Map<Control, Label> errorLabels = new HashMap<>();
    private final Map<Control, String> originalStyles = new HashMap<>();

    // Configuration
    private boolean realTimeValidation = true;
    private boolean showSuccessIndicators = false;

    /**
     * Constructeur
     */
    public FormValidator() {
        // Configuration par défaut
    }

    // ==================== AJOUT DE RÈGLES DE VALIDATION ====================

    /**
     * Ajoute une règle de champ obligatoire
     */
    public FormValidator addRequired(Control field, String message) {
        rules.add(new RequiredRule(field, message));
        setupRealTimeValidation(field);
        return this;
    }

    /**
     * Ajoute une validation d'email
     */
    public FormValidator addEmail(TextField field, String message) {
        if (message == null) {
            message = "Format d'email invalide (exemple: nom@domaine.com)";
        }
        rules.add(new EmailRule(field, message));
        setupRealTimeValidation(field);
        return this;
    }

    /**
     * Ajoute une validation de téléphone
     */
    public FormValidator addPhone(TextField field, String message) {
        if (message == null) {
            message = "Numéro de téléphone invalide (8-15 chiffres)";
        }
        rules.add(new PhoneRule(field, message));
        setupRealTimeValidation(field);
        return this;
    }

    /**
     * Ajoute une validation numérique
     */
    public FormValidator addNumeric(TextField field, String message) {
        if (message == null) {
            message = "Veuillez saisir un nombre valide";
        }
        rules.add(new NumericRule(field, message));
        setupRealTimeValidation(field);
        return this;
    }

    /**
     * Ajoute une validation de montant
     */
    public FormValidator addMontant(TextField field, String message) {
        if (message == null) {
            message = "Veuillez saisir un montant valide (ex: 1000 ou 1000.50)";
        }
        rules.add(new MontantRule(field, message));
        setupRealTimeValidation(field);
        return this;
    }

    /**
     * Ajoute une validation de longueur minimale
     */
    public FormValidator addMinLength(TextField field, int minLength, String message) {
        if (message == null) {
            message = "Ce champ doit contenir au moins " + minLength + " caractères";
        }
        rules.add(new MinLengthRule(field, minLength, message));
        setupRealTimeValidation(field);
        return this;
    }

    /**
     * Ajoute une validation de longueur maximale
     */
    public FormValidator addMaxLength(TextField field, int maxLength, String message) {
        if (message == null) {
            message = "Ce champ ne peut pas dépasser " + maxLength + " caractères";
        }
        rules.add(new MaxLengthRule(field, maxLength, message));
        setupRealTimeValidation(field);
        return this;
    }

    /**
     * Ajoute une validation de date
     */
    public FormValidator addDateNotFuture(DatePicker field, String message) {
        if (message == null) {
            message = "La date ne peut pas être dans le futur";
        }
        rules.add(new DateNotFutureRule(field, message));
        setupRealTimeValidation(field);
        return this;
    }

    /**
     * Ajoute une validation personnalisée
     */
    public FormValidator addCustom(Control field, Predicate<Control> validator, String message) {
        rules.add(new CustomRule(field, validator, message));
        setupRealTimeValidation(field);
        return this;
    }

    /**
     * Ajoute une validation de comparaison (ex: confirmer mot de passe)
     */
    public FormValidator addComparison(TextField field1, TextField field2, String message) {
        if (message == null) {
            message = "Les deux champs doivent être identiques";
        }
        rules.add(new ComparisonRule(field1, field2, message));
        setupRealTimeValidation(field1);
        setupRealTimeValidation(field2);
        return this;
    }

    // ==================== VALIDATION ET AFFICHAGE ====================

    /**
     * Valide tous les champs
     */
    public ValidationResult validate() {
        ValidationResult result = new ValidationResult();
        clearAllErrors();

        for (ValidationRule rule : rules) {
            if (!rule.validate()) {
                result.addError(rule.getField(), rule.getErrorMessage());
                showError(rule.getField(), rule.getErrorMessage());
            } else if (showSuccessIndicators) {
                showSuccess(rule.getField());
            }
        }

        return result;
    }

    /**
     * Valide un champ spécifique
     */
    public ValidationResult validateField(Control field) {
        ValidationResult result = new ValidationResult();
        clearFieldError(field);

        for (ValidationRule rule : rules) {
            if (rule.getField() == field) {
                if (!rule.validate()) {
                    result.addError(field, rule.getErrorMessage());
                    showError(field, rule.getErrorMessage());
                } else if (showSuccessIndicators) {
                    showSuccess(field);
                }
                break;
            }
        }

        return result;
    }

    /**
     * Configuration de la validation en temps réel
     */
    private void setupRealTimeValidation(Control field) {
        if (!realTimeValidation) {
            return;
        }

        if (field instanceof TextField) {
            TextField textField = (TextField) field;
            textField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                if (wasFocused && !isFocused) {
                    // Validation à la perte de focus
                    Platform.runLater(() -> validateField(field));
                }
            });
        } else if (field instanceof DatePicker) {
            DatePicker datePicker = (DatePicker) field;
            datePicker.valueProperty().addListener((obs, oldValue, newValue) -> {
                Platform.runLater(() -> validateField(field));
            });
        } else if (field instanceof ComboBox) {
            ComboBox<?> comboBox = (ComboBox<?>) field;
            comboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
                Platform.runLater(() -> validateField(field));
            });
        }
    }

    // ==================== AFFICHAGE DES ERREURS ====================

    /**
     * Affiche une erreur sur un champ
     */
    private void showError(Control field, String message) {
        // Sauvegarder le style original
        if (!originalStyles.containsKey(field)) {
            originalStyles.put(field, field.getStyle());
        }

        // Appliquer le style d'erreur
        field.getStyleClass().removeAll("field-success", "field-warning");
        field.getStyleClass().add("field-error");

        // Créer ou mettre à jour le label d'erreur
        Label errorLabel = getOrCreateErrorLabel(field);
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.getStyleClass().clear();
        errorLabel.getStyleClass().addAll("error-label");

        logger.debug("Erreur affichée pour le champ: {}", message);
    }

    /**
     * Affiche un indicateur de succès
     */
    private void showSuccess(Control field) {
        // Retirer les styles d'erreur
        field.getStyleClass().removeAll("field-error", "field-warning");
        field.getStyleClass().add("field-success");

        // Masquer le label d'erreur
        Label errorLabel = errorLabels.get(field);
        if (errorLabel != null) {
            errorLabel.setVisible(false);
        }
    }

    /**
     * Affiche un avertissement
     */
    public void showWarning(Control field, String message) {
        field.getStyleClass().removeAll("field-error", "field-success");
        field.getStyleClass().add("field-warning");

        Label errorLabel = getOrCreateErrorLabel(field);
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.getStyleClass().clear();
        errorLabel.getStyleClass().addAll("warning-label");
    }

    /**
     * Efface l'erreur d'un champ
     */
    private void clearFieldError(Control field) {
        // Restaurer le style original
        field.getStyleClass().removeAll("field-error", "field-warning", "field-success");

        String originalStyle = originalStyles.get(field);
        if (originalStyle != null) {
            field.setStyle(originalStyle);
        }

        // Masquer le label d'erreur
        Label errorLabel = errorLabels.get(field);
        if (errorLabel != null) {
            errorLabel.setVisible(false);
        }
    }

    /**
     * Efface toutes les erreurs
     */
    private void clearAllErrors() {
        for (Control field : errorLabels.keySet()) {
            clearFieldError(field);
        }
    }

    /**
     * Obtient ou crée un label d'erreur pour un champ
     */
    private Label getOrCreateErrorLabel(Control field) {
        Label errorLabel = errorLabels.get(field);

        if (errorLabel == null) {
            errorLabel = new Label();
            errorLabel.getStyleClass().add("error-label");
            errorLabel.setVisible(false);
            errorLabel.setWrapText(true);
            errorLabel.setMaxWidth(Double.MAX_VALUE);

            // Positionner le label selon le parent
            positionErrorLabel(field, errorLabel);

            errorLabels.put(field, errorLabel);
        }

        return errorLabel;
    }

    /**
     * Positionne le label d'erreur selon le type de conteneur parent
     */
    private void positionErrorLabel(Control field, Label errorLabel) {
        if (field.getParent() instanceof VBox) {
            VBox parent = (VBox) field.getParent();
            int index = parent.getChildren().indexOf(field);
            if (index >= 0 && index < parent.getChildren().size() - 1) {
                parent.getChildren().add(index + 1, errorLabel);
            } else {
                parent.getChildren().add(errorLabel);
            }
        } else if (field.getParent() instanceof GridPane) {
            GridPane parent = (GridPane) field.getParent();
            Integer row = GridPane.getRowIndex(field);
            Integer col = GridPane.getColumnIndex(field);

            if (row != null && col != null) {
                GridPane.setRowIndex(errorLabel, row);
                GridPane.setColumnIndex(errorLabel, col);
                GridPane.setColumnSpan(errorLabel, 2);
                parent.getChildren().add(errorLabel);
            }
        }
        // Autres types de conteneurs peuvent être ajoutés ici
    }

    // ==================== CONFIGURATION ====================

    /**
     * Active/désactive la validation en temps réel
     */
    public FormValidator setRealTimeValidation(boolean enabled) {
        this.realTimeValidation = enabled;
        return this;
    }

    /**
     * Active/désactive les indicateurs de succès
     */
    public FormValidator setShowSuccessIndicators(boolean enabled) {
        this.showSuccessIndicators = enabled;
        return this;
    }

    // ==================== CLASSES INTERNES POUR LES RÈGLES ====================

    /**
     * Interface pour les règles de validation
     */
    private interface ValidationRule {
        Control getField();
        boolean validate();
        String getErrorMessage();
    }

    /**
     * Règle de champ obligatoire
     */
    private static class RequiredRule implements ValidationRule {
        private final Control field;
        private final String message;

        public RequiredRule(Control field, String message) {
            this.field = field;
            this.message = message;
        }

        @Override
        public Control getField() {
            return field;
        }

        @Override
        public boolean validate() {
            if (field instanceof TextField) {
                String text = ((TextField) field).getText();
                return text != null && !text.trim().isEmpty();
            } else if (field instanceof ComboBox) {
                return ((ComboBox<?>) field).getValue() != null;
            } else if (field instanceof DatePicker) {
                return ((DatePicker) field).getValue() != null;
            }
            return true;
        }

        @Override
        public String getErrorMessage() {
            return message;
        }
    }

    /**
     * Règle de validation d'email
     */
    private static class EmailRule implements ValidationRule {
        private final TextField field;
        private final String message;

        public EmailRule(TextField field, String message) {
            this.field = field;
            this.message = message;
        }

        @Override
        public Control getField() {
            return field;
        }

        @Override
        public boolean validate() {
            String text = field.getText();
            if (text == null || text.trim().isEmpty()) {
                return true; // Optionnel si vide
            }
            return EMAIL_PATTERN.matcher(text.trim()).matches();
        }

        @Override
        public String getErrorMessage() {
            return message;
        }
    }

    /**
     * Règle de validation de téléphone
     */
    private static class PhoneRule implements ValidationRule {
        private final TextField field;
        private final String message;

        public PhoneRule(TextField field, String message) {
            this.field = field;
            this.message = message;
        }

        @Override
        public Control getField() {
            return field;
        }

        @Override
        public boolean validate() {
            String text = field.getText();
            if (text == null || text.trim().isEmpty()) {
                return true; // Optionnel si vide
            }
            return PHONE_PATTERN.matcher(text.trim()).matches();
        }

        @Override
        public String getErrorMessage() {
            return message;
        }
    }

    /**
     * Règle de validation numérique
     */
    private static class NumericRule implements ValidationRule {
        private final TextField field;
        private final String message;

        public NumericRule(TextField field, String message) {
            this.field = field;
            this.message = message;
        }

        @Override
        public Control getField() {
            return field;
        }

        @Override
        public boolean validate() {
            String text = field.getText();
            if (text == null || text.trim().isEmpty()) {
                return true; // Optionnel si vide
            }
            try {
                Double.parseDouble(text.trim());
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        @Override
        public String getErrorMessage() {
            return message;
        }
    }

    /**
     * Règle de validation de montant
     */
    private static class MontantRule implements ValidationRule {
        private final TextField field;
        private final String message;

        public MontantRule(TextField field, String message) {
            this.field = field;
            this.message = message;
        }

        @Override
        public Control getField() {
            return field;
        }

        @Override
        public boolean validate() {
            String text = field.getText();
            if (text == null || text.trim().isEmpty()) {
                return true; // Optionnel si vide
            }
            try {
                BigDecimal montant = new BigDecimal(text.trim().replace(",", "."));
                return montant.compareTo(BigDecimal.ZERO) >= 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        @Override
        public String getErrorMessage() {
            return message;
        }
    }

    /**
     * Règle de longueur minimale
     */
    private static class MinLengthRule implements ValidationRule {
        private final TextField field;
        private final int minLength;
        private final String message;

        public MinLengthRule(TextField field, int minLength, String message) {
            this.field = field;
            this.minLength = minLength;
            this.message = message;
        }

        @Override
        public Control getField() {
            return field;
        }

        @Override
        public boolean validate() {
            String text = field.getText();
            if (text == null) {
                return false;
            }
            return text.trim().length() >= minLength;
        }

        @Override
        public String getErrorMessage() {
            return message;
        }
    }

    /**
     * Règle de longueur maximale
     */
    private static class MaxLengthRule implements ValidationRule {
        private final TextField field;
        private final int maxLength;
        private final String message;

        public MaxLengthRule(TextField field, int maxLength, String message) {
            this.field = field;
            this.maxLength = maxLength;
            this.message = message;
        }

        @Override
        public Control getField() {
            return field;
        }

        @Override
        public boolean validate() {
            String text = field.getText();
            if (text == null) {
                return true;
            }
            return text.length() <= maxLength;
        }

        @Override
        public String getErrorMessage() {
            return message;
        }
    }

    /**
     * Règle de date pas dans le futur
     */
    private static class DateNotFutureRule implements ValidationRule {
        private final DatePicker field;
        private final String message;

        public DateNotFutureRule(DatePicker field, String message) {
            this.field = field;
            this.message = message;
        }

        @Override
        public Control getField() {
            return field;
        }

        @Override
        public boolean validate() {
            LocalDate date = field.getValue();
            if (date == null) {
                return true; // Optionnel si vide
            }
            return !date.isAfter(LocalDate.now());
        }

        @Override
        public String getErrorMessage() {
            return message;
        }
    }

    /**
     * Règle personnalisée
     */
    private static class CustomRule implements ValidationRule {
        private final Control field;
        private final Predicate<Control> validator;
        private final String message;

        public CustomRule(Control field, Predicate<Control> validator, String message) {
            this.field = field;
            this.validator = validator;
            this.message = message;
        }

        @Override
        public Control getField() {
            return field;
        }

        @Override
        public boolean validate() {
            return validator.test(field);
        }

        @Override
        public String getErrorMessage() {
            return message;
        }
    }

    /**
     * Règle de comparaison entre deux champs
     */
    private static class ComparisonRule implements ValidationRule {
        private final TextField field1;
        private final TextField field2;
        private final String message;

        public ComparisonRule(TextField field1, TextField field2, String message) {
            this.field1 = field1;
            this.field2 = field2;
            this.message = message;
        }

        @Override
        public Control getField() {
            return field2; // L'erreur s'affiche sur le deuxième champ
        }

        @Override
        public boolean validate() {
            String text1 = field1.getText();
            String text2 = field2.getText();

            if (text1 == null && text2 == null) {
                return true;
            }

            return Objects.equals(text1, text2);
        }

        @Override
        public String getErrorMessage() {
            return message;
        }
    }
}