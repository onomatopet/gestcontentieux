package com.regulation.contentieux.controller;

import com.regulation.contentieux.dao.ContrevenantDAO;
import com.regulation.contentieux.model.Contrevenant;
import com.regulation.contentieux.service.AuthenticationService;
import com.regulation.contentieux.service.ValidationService;
import com.regulation.contentieux.util.AlertUtil;
import com.regulation.contentieux.util.CurrencyFormatter;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.LocalDate;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

/**
 * Contrôleur pour le formulaire de contrevenant - SUIT LE PATTERN ÉTABLI
 * Respecte exactement la logique des contrôleurs existants
 */
public class ContrevenantFormController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(ContrevenantFormController.class);

    // Constantes pour la validation
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\+241\\s?[0-9]{2}\\s?[0-9]{2}\\s?[0-9]{2}\\s?[0-9]{2}$");

    // FXML - En-tête
    @FXML private Label formTitleLabel;
    @FXML private Label modeLabel;

    // FXML - Informations générales
    @FXML private TextField codeField;
    @FXML private Button generateCodeButton;
    @FXML private ComboBox<String> typePersonneComboBox;
    @FXML private TextField nomCompletField;

    // FXML - Coordonnées
    @FXML private TextField telephoneField;
    @FXML private TextField emailField;
    @FXML private TextArea adresseField;

    // FXML - Personne physique
    @FXML private VBox personnePhysiqueBox;
    @FXML private DatePicker dateNaissancePicker;
    @FXML private TextField professionField;

    // FXML - Personne morale
    @FXML private VBox personneMoraleBox;
    @FXML private TextField numeroRCCMField;
    @FXML private TextField secteurActiviteField;
    @FXML private TextField representantLegalField;

    // FXML - Historique des affaires
    @FXML private VBox affairesHistoryBox;
    @FXML private Label affairesCountLabel;
    @FXML private Button viewAffairesButton;
    @FXML private Label totalAmendesLabel;
    @FXML private Label montantEncaisseLabel;
    @FXML private Label affairesOuvertesLabel;
    @FXML private Label soldeRestantLabel;

    // FXML - Actions
    @FXML private Button cancelButton;
    @FXML private Button resetButton;
    @FXML private Button saveButton;

    // Services et données - SUIT LE PATTERN ÉTABLI
    private ContrevenantDAO contrevenantDAO;
    private AuthenticationService authService;
    private ValidationService validationService;

    // État du formulaire
    private boolean isEditMode = false;
    private Contrevenant currentContrevenant;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialisation des services - SUIT LE PATTERN ÉTABLI
        contrevenantDAO = new ContrevenantDAO();
        authService = AuthenticationService.getInstance();
        validationService = new ValidationService();

        setupUI();
        setupEventHandlers();
        setupValidation();

        logger.info("Contrôleur de formulaire de contrevenant initialisé");
    }

    /**
     * Configuration initiale de l'interface - SUIT LE PATTERN ÉTABLI
     */
    private void setupUI() {
        // Configuration du ComboBox type de personne
        setupTypePersonneComboBox();

        // Configuration des champs
        adresseField.setWrapText(true);

        // Configuration initiale
        updateUIMode();
    }

    public void initializeForNew() {
        isEditMode = false;
        clearForm();
        updateUIMode();
        Platform.runLater(() -> {
            if (codeField != null && codeField.getText().isEmpty()) {
                String newCode = contrevenantService.generateNextCode();
                codeField.setText(newCode);
            }
        });
    }

    /**
     * Configuration des gestionnaires d'événements - SUIT LE PATTERN ÉTABLI
     */
    private void setupEventHandlers() {
        // Génération du code
        generateCodeButton.setOnAction(e -> generateCode());

        // Changement de type de personne
        typePersonneComboBox.setOnAction(e -> handleTypePersonneChange());

        // Boutons d'action
        cancelButton.setOnAction(e -> handleCancel());
        resetButton.setOnAction(e -> handleReset());
        saveButton.setOnAction(e -> handleSave());

        // Affichage des affaires
        viewAffairesButton.setOnAction(e -> handleViewAffaires());
    }

    /**
     * Configuration de la validation en temps réel - SUIT LE PATTERN ÉTABLI
     */
    private void setupValidation() {
        // Validation de l'email
        emailField.textProperty().addListener((obs, oldVal, newVal) -> validateEmail(newVal));

        // Validation du téléphone
        telephoneField.textProperty().addListener((obs, oldVal, newVal) -> validatePhone(newVal));

        // Validation du nom complet
        nomCompletField.textProperty().addListener((obs, oldVal, newVal) -> validateNomComplet(newVal));
    }

    /**
     * Configuration du ComboBox type de personne - SUIT LE PATTERN ÉTABLI
     */
    private void setupTypePersonneComboBox() {
        typePersonneComboBox.getItems().addAll("PHYSIQUE", "MORALE");
        typePersonneComboBox.setConverter(new StringConverter<String>() {
            @Override
            public String toString(String type) {
                if (type == null) return "";
                return "PHYSIQUE".equals(type) ? "Personne physique" : "Personne morale";
            }

            @Override
            public String fromString(String string) {
                return null;
            }
        });
    }

    // Actions du formulaire - SUIT LE PATTERN ÉTABLI

    private void generateCode() {
        try {
            String nextCode = contrevenantDAO.generateNextCode();
            codeField.setText(nextCode);
            logger.info("Code contrevenant généré: {}", nextCode);
        } catch (Exception e) {
            logger.error("Erreur lors de la génération du code", e);
            AlertUtil.showErrorAlert("Erreur de génération",
                    "Impossible de générer le code contrevenant",
                    "Vérifiez la connexion à la base de données.");
        }
    }

    private void handleTypePersonneChange() {
        String selectedType = typePersonneComboBox.getValue();

        if ("PHYSIQUE".equals(selectedType)) {
            showPersonnePhysiqueFields();
        } else if ("MORALE".equals(selectedType)) {
            showPersonneMoraleFields();
        } else {
            hideAllSpecificFields();
        }
    }

    private void showPersonnePhysiqueFields() {
        personnePhysiqueBox.setVisible(true);
        personnePhysiqueBox.setManaged(true);
        personneMoraleBox.setVisible(false);
        personneMoraleBox.setManaged(false);

        // Mise à jour du prompt text
        nomCompletField.setPromptText("Nom et prénom");
    }

    private void showPersonneMoraleFields() {
        personneMoraleBox.setVisible(true);
        personneMoraleBox.setManaged(true);
        personnePhysiqueBox.setVisible(false);
        personnePhysiqueBox.setManaged(false);

        // Mise à jour du prompt text
        nomCompletField.setPromptText("Raison sociale");
    }

    private void hideAllSpecificFields() {
        personnePhysiqueBox.setVisible(false);
        personnePhysiqueBox.setManaged(false);
        personneMoraleBox.setVisible(false);
        personneMoraleBox.setManaged(false);

        nomCompletField.setPromptText("Nom complet ou raison sociale");
    }

    private void handleSave() {
        if (validateForm()) {
            saveContrevenant();
        }
    }

    private void handleReset() {
        if (AlertUtil.showConfirmAlert("Confirmation",
                "Réinitialiser le formulaire",
                "Voulez-vous vraiment réinitialiser tous les champs ?")) {
            resetForm();
        }
    }

    private void handleCancel() {
        if (hasUnsavedChanges()) {
            if (AlertUtil.showConfirmAlert("Confirmation",
                    "Annuler les modifications",
                    "Vous avez des modifications non sauvegardées. Voulez-vous vraiment annuler ?")) {
                closeForm();
            }
        } else {
            closeForm();
        }
    }

    private void handleViewAffaires() {
        if (currentContrevenant != null) {
            logger.info("Affichage des affaires pour le contrevenant: {}", currentContrevenant.getCode());
            AlertUtil.showInfoAlert("Affaires du contrevenant",
                    "Fonctionnalité en développement",
                    "La liste des affaires sera disponible prochainement.");
        }
    }

    // Méthodes de validation - SUIT LE PATTERN ÉTABLI

    private void validateEmail(String email) {
        if (email != null && !email.trim().isEmpty()) {
            if (EMAIL_PATTERN.matcher(email.trim()).matches()) {
                emailField.setStyle("");
            } else {
                emailField.setStyle("-fx-border-color: red;");
            }
        } else {
            emailField.setStyle("");
        }
    }

    private void validatePhone(String phone) {
        if (phone != null && !phone.trim().isEmpty()) {
            if (PHONE_PATTERN.matcher(phone.trim()).matches()) {
                telephoneField.setStyle("");
            } else {
                telephoneField.setStyle("-fx-border-color: red;");
            }
        } else {
            telephoneField.setStyle("");
        }
    }

    private void validateNomComplet(String nom) {
        if (nom != null && nom.trim().length() >= 2) {
            nomCompletField.setStyle("");
        } else if (nom != null && !nom.trim().isEmpty()) {
            nomCompletField.setStyle("-fx-border-color: red;");
        } else {
            nomCompletField.setStyle("");
        }
    }

    /**
     * Validation complète du formulaire - SUIT LE PATTERN ÉTABLI
     */
    private boolean validateForm() {
        StringBuilder errors = new StringBuilder();

        // Validation du code
        if (codeField.getText() == null || codeField.getText().trim().isEmpty()) {
            errors.append("• Le code contrevenant est obligatoire\n");
        }

        // Validation du nom complet
        if (nomCompletField.getText() == null || nomCompletField.getText().trim().length() < 2) {
            errors.append("• Le nom complet doit contenir au moins 2 caractères\n");
        }

        // Validation du type de personne
        if (typePersonneComboBox.getValue() == null) {
            errors.append("• Le type de personne est obligatoire\n");
        }

        // Validation de l'email (si renseigné)
        String email = emailField.getText();
        if (email != null && !email.trim().isEmpty() && !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            errors.append("• L'adresse email n'est pas valide\n");
        }

        // Validation du téléphone (si renseigné)
        String phone = telephoneField.getText();
        if (phone != null && !phone.trim().isEmpty() && !PHONE_PATTERN.matcher(phone.trim()).matches()) {
            errors.append("• Le numéro de téléphone doit être au format gabonais (+241 XX XX XX XX)\n");
        }

        // Validations spécifiques selon le type
        if ("PHYSIQUE".equals(typePersonneComboBox.getValue())) {
            validatePersonnePhysique(errors);
        } else if ("MORALE".equals(typePersonneComboBox.getValue())) {
            validatePersonneMorale(errors);
        }

        if (errors.length() > 0) {
            AlertUtil.showErrorAlert("Erreurs de validation",
                    "Veuillez corriger les erreurs suivantes :",
                    errors.toString());
            return false;
        }

        return true;
    }

    private void validatePersonnePhysique(StringBuilder errors) {
        // Validation de la date de naissance (optionnelle mais si renseignée, doit être cohérente)
        if (dateNaissancePicker.getValue() != null) {
            LocalDate birthDate = dateNaissancePicker.getValue();
            LocalDate now = LocalDate.now();

            if (birthDate.isAfter(now)) {
                errors.append("• La date de naissance ne peut pas être dans le futur\n");
            } else if (birthDate.isBefore(now.minusYears(120))) {
                errors.append("• La date de naissance semble incorrecte (plus de 120 ans)\n");
            }
        }
    }

    private void validatePersonneMorale(StringBuilder errors) {
        // Validation du RCCM (optionnel mais format si renseigné)
        String rccm = numeroRCCMField.getText();
        if (rccm != null && !rccm.trim().isEmpty() && rccm.trim().length() < 5) {
            errors.append("• Le numéro RCCM semble trop court\n");
        }

        // Le représentant légal est recommandé pour les personnes morales
        String representant = representantLegalField.getText();
        if (representant == null || representant.trim().isEmpty()) {
            // Note: Ce n'est qu'un avertissement, pas une erreur bloquante
            logger.info("Représentant légal non renseigné pour la personne morale");
        }
    }

    /**
     * Sauvegarde du contrevenant - SUIT LE PATTERN ÉTABLI
     */
    private void saveContrevenant() {
        Task<Contrevenant> saveTask = new Task<Contrevenant>() {
            @Override
            protected Contrevenant call() throws Exception {
                Contrevenant contrevenant = isEditMode ? currentContrevenant : new Contrevenant();

                // Remplissage des données de base
                contrevenant.setCode(codeField.getText().trim());
                contrevenant.setNomComplet(nomCompletField.getText().trim());
                contrevenant.setTypePersonne(typePersonneComboBox.getValue());

                // Coordonnées
                contrevenant.setTelephone(telephoneField.getText() != null ? telephoneField.getText().trim() : null);
                contrevenant.setEmail(emailField.getText() != null ? emailField.getText().trim() : null);
                contrevenant.setAdresse(adresseField.getText() != null ? adresseField.getText().trim() : null);

                // Sauvegarde selon le mode
                if (isEditMode) {
                    return contrevenantDAO.update(contrevenant);
                } else {
                    return contrevenantDAO.save(contrevenant);
                }
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    Contrevenant savedContrevenant = getValue();
                    String message = isEditMode ? "Contrevenant modifié avec succès" : "Contrevenant créé avec succès";

                    AlertUtil.showSuccessAlert("Sauvegarde réussie", message,
                            "Code: " + savedContrevenant.getCode());

                    // Mise à jour de l'état
                    currentContrevenant = savedContrevenant;
                    isEditMode = true;
                    updateUIMode();
                    loadAffairesHistory();

                    logger.info("Contrevenant sauvegardé: {}", savedContrevenant.getCode());
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors de la sauvegarde", getException());
                    AlertUtil.showErrorAlert("Erreur de sauvegarde",
                            "Impossible de sauvegarder le contrevenant",
                            "Vérifiez les données et réessayez.");
                });
            }
        };

        Thread saveThread = new Thread(saveTask);
        saveThread.setDaemon(true);
        saveThread.start();
    }

    /**
     * Réinitialise le formulaire - SUIT LE PATTERN ÉTABLI
     */
    private void resetForm() {
        codeField.clear();
        nomCompletField.clear();
        typePersonneComboBox.setValue(null);
        telephoneField.clear();
        emailField.clear();
        adresseField.clear();

        dateNaissancePicker.setValue(null);
        professionField.clear();

        numeroRCCMField.clear();
        secteurActiviteField.clear();
        representantLegalField.clear();

        // Masquer les sections spécifiques
        hideAllSpecificFields();

        // Masquer l'historique des affaires
        affairesHistoryBox.setVisible(false);
        affairesHistoryBox.setManaged(false);

        // Réinitialisation des styles d'erreur
        clearValidationStyles();

        logger.info("Formulaire réinitialisé");
    }

    private void clearValidationStyles() {
        nomCompletField.setStyle("");
        emailField.setStyle("");
        telephoneField.setStyle("");
    }

    /**
     * Vérifie s'il y a des modifications non sauvegardées
     */
    private boolean hasUnsavedChanges() {
        if (currentContrevenant == null) {
            // Mode création - vérifier si des champs sont remplis
            return !codeField.getText().trim().isEmpty() ||
                    !nomCompletField.getText().trim().isEmpty() ||
                    typePersonneComboBox.getValue() != null ||
                    !telephoneField.getText().trim().isEmpty() ||
                    !emailField.getText().trim().isEmpty();
        } else {
            // Mode édition - comparer avec les valeurs originales
            return !codeField.getText().equals(currentContrevenant.getCode()) ||
                    !nomCompletField.getText().equals(currentContrevenant.getNomComplet()) ||
                    !typePersonneComboBox.getValue().equals(currentContrevenant.getTypePersonne());
        }
    }

    /**
     * Met à jour l'interface selon le mode - SUIT LE PATTERN ÉTABLI
     */
    private void updateUIMode() {
        if (isEditMode) {
            formTitleLabel.setText("Modifier le contrevenant");
            modeLabel.setText("Mode édition");
            saveButton.setText("Mettre à jour");

            // Afficher l'historique des affaires en mode édition
            affairesHistoryBox.setVisible(true);
            affairesHistoryBox.setManaged(true);
        } else {
            formTitleLabel.setText("Nouveau contrevenant");
            modeLabel.setText("Mode création");
            saveButton.setText("Enregistrer");

            // Masquer l'historique en mode création
            affairesHistoryBox.setVisible(false);
            affairesHistoryBox.setManaged(false);
        }
    }

    /**
     * Ferme le formulaire
     */
    private void closeForm() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    /**
     * Charge l'historique des affaires - SUIT LE PATTERN ÉTABLI
     */
    private void loadAffairesHistory() {
        if (currentContrevenant == null) return;

        Task<Void> loadTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                // TODO: Charger les statistiques des affaires
                // Pour l'instant, affichage de données fictives
                Platform.runLater(() -> {
                    affairesCountLabel.setText("(3 affaires)");
                    totalAmendesLabel.setText(CurrencyFormatter.format(2500000.0));
                    montantEncaisseLabel.setText(CurrencyFormatter.format(1500000.0));
                    affairesOuvertesLabel.setText("2");
                    soldeRestantLabel.setText(CurrencyFormatter.format(1000000.0));
                });
                return null;
            }
        };

        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
        loadThread.start();
    }

    // Méthodes publiques pour l'intégration

    /**
     * Configure le formulaire en mode édition - SUIT LE PATTERN ÉTABLI
     */
    public void setContrevenantToEdit(Contrevenant contrevenant) {
        this.currentContrevenant = contrevenant;
        this.isEditMode = true;

        Platform.runLater(() -> {
            fillFormWithContrevenant(contrevenant);
            updateUIMode();
            loadAffairesHistory();
        });
    }

    /**
     * Remplit le formulaire avec les données d'un contrevenant - SUIT LE PATTERN ÉTABLI
     */
    private void fillFormWithContrevenant(Contrevenant contrevenant) {
        codeField.setText(contrevenant.getCode());
        nomCompletField.setText(contrevenant.getNomComplet());
        typePersonneComboBox.setValue(contrevenant.getTypePersonne());

        if (contrevenant.getTelephone() != null) {
            telephoneField.setText(contrevenant.getTelephone());
        }
        if (contrevenant.getEmail() != null) {
            emailField.setText(contrevenant.getEmail());
        }
        if (contrevenant.getAdresse() != null) {
            adresseField.setText(contrevenant.getAdresse());
        }

        // Déclencher l'affichage des champs spécifiques
        handleTypePersonneChange();

        logger.info("Formulaire rempli avec le contrevenant: {}", contrevenant.getCode());
    }

    /**
     * Configure le formulaire en mode création avec des valeurs par défaut
     */
    public void setDefaultValues(String code, String typePersonne) {
        Platform.runLater(() -> {
            if (code != null) {
                codeField.setText(code);
            }
            if (typePersonne != null) {
                typePersonneComboBox.setValue(typePersonne);
                handleTypePersonneChange();
            }
        });
    }
}