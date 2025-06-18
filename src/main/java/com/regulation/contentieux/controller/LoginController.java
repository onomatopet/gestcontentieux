package com.regulation.contentieux.controller;

import com.regulation.contentieux.service.AuthenticationService;
import com.regulation.contentieux.util.AlertUtil;
import com.regulation.contentieux.util.FXMLLoaderUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Contrôleur pour la vue de connexion
 */
public class LoginController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private CheckBox rememberMeCheckBox;
    @FXML private Button loginButton;
    @FXML private Hyperlink forgotPasswordLink;
    @FXML private Label statusLabel;

    private AuthenticationService authService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        authService = AuthenticationService.getInstance();

        // Configuration initiale
        setupUI();
        setupEventHandlers();

        logger.debug("LoginController initialisé");
    }

    /**
     * Configuration de l'interface utilisateur
     */
    private void setupUI() {
        // Focus sur le champ username au démarrage
        Platform.runLater(() -> usernameField.requestFocus());

        // Configuration du bouton de connexion
        loginButton.setDefaultButton(true);

        // Validation en temps réel
        usernameField.textProperty().addListener((obs, oldVal, newVal) -> validateForm());
        passwordField.textProperty().addListener((obs, oldVal, newVal) -> validateForm());

        // Validation initiale
        validateForm();
    }

    /**
     * Configuration des gestionnaires d'événements
     */
    private void setupEventHandlers() {
        // Action du bouton de connexion
        loginButton.setOnAction(event -> handleLogin());

        // Action sur Enter dans les champs
        usernameField.setOnAction(event -> {
            if (passwordField.getText().isEmpty()) {
                passwordField.requestFocus();
            } else {
                handleLogin();
            }
        });

        passwordField.setOnAction(event -> handleLogin());

        // Lien mot de passe oublié
        forgotPasswordLink.setOnAction(event -> handleForgotPassword());
    }

    /**
     * Gère la tentative de connexion
     */
    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (!validateInput(username, password)) {
            return;
        }

        // Désactivation temporaire du bouton
        loginButton.setDisable(true);
        statusLabel.setText("Connexion en cours...");

        // Authentification (dans un thread séparé si nécessaire)
        try {
            AuthenticationService.AuthenticationResult result = authService.authenticate(username, password);

            if (result.isSuccess()) {
                statusLabel.setText("Connexion réussie !");
                logger.info("Connexion réussie pour: {}", username);

                // Mise à jour de la préférence "Se souvenir de moi"
                if (rememberMeCheckBox.isSelected()) {
                    // TODO: Sauvegarder les préférences
                    logger.debug("Préférence 'Se souvenir de moi' activée pour: {}", username);
                }

                // Chargement de la vue principale
                loadMainView();

            } else {
                statusLabel.setText("Échec de la connexion");
                AlertUtil.showErrorAlert(
                        "Erreur de connexion",
                        "Échec de l'authentification",
                        result.getMessage()
                );

                // Focus sur le champ approprié
                if (username.isEmpty()) {
                    usernameField.requestFocus();
                } else {
                    passwordField.selectAll();
                    passwordField.requestFocus();
                }
            }

        } catch (Exception e) {
            logger.error("Erreur lors de la connexion", e);
            statusLabel.setText("Erreur système");
            AlertUtil.showErrorAlert(
                    "Erreur système",
                    "Une erreur inattendue s'est produite",
                    "Veuillez réessayer ou contacter l'administrateur."
            );
        } finally {
            loginButton.setDisable(false);
        }
    }

    /**
     * Valide les entrées utilisateur
     */
    private boolean validateInput(String username, String password) {
        if (username.isEmpty()) {
            statusLabel.setText("Nom d'utilisateur requis");
            usernameField.requestFocus();
            return false;
        }

        if (password.isEmpty()) {
            statusLabel.setText("Mot de passe requis");
            passwordField.requestFocus();
            return false;
        }

        return true;
    }

    /**
     * Valide le formulaire en temps réel
     */
    private void validateForm() {
        boolean isValid = !usernameField.getText().trim().isEmpty() &&
                !passwordField.getText().isEmpty();

        loginButton.setDisable(!isValid);

        if (isValid) {
            statusLabel.setText("Prêt à se connecter");
        } else {
            statusLabel.setText("Saisissez vos identifiants");
        }
    }

    /**
     * Gère le lien "Mot de passe oublié"
     */
    @FXML
    private void handleForgotPassword() {
        AlertUtil.showInfoAlert(
                "Mot de passe oublié",
                "Récupération de mot de passe",
                "Contactez votre administrateur système pour réinitialiser votre mot de passe."
        );
    }

    /**
     * Charge la vue principale après connexion réussie
     */
    private void loadMainView() {
        try {
            logger.info("Chargement de l'interface principale...");

            // Chargement de la vue principale
            Scene mainScene = FXMLLoaderUtil.loadScene("view/main.fxml", 1400, 900);

            // Récupération de la fenêtre actuelle
            Stage stage = (Stage) loginButton.getScene().getWindow();

            // Configuration de la fenêtre principale
            stage.setTitle("Gestion des Affaires Contentieuses - v1.0.0");
            stage.setScene(mainScene);
            stage.setMaximized(true);
            stage.setMinWidth(1200);
            stage.setMinHeight(800);

            // Centrer sur l'écran si pas maximisé
            stage.centerOnScreen();

            logger.info("Interface principale chargée avec succès");

        } catch (Exception e) {
            logger.error("Erreur lors du chargement de la vue principale", e);
            AlertUtil.showErrorAlert(
                    "Erreur de chargement",
                    "Impossible de charger l'interface principale",
                    "Erreur technique : " + e.getMessage()
            );

            // En cas d'erreur, déconnecter l'utilisateur
            authService.logout();
            clearFields();
        }
    }

    /**
     * Méthode pour pré-remplir les champs (utile pour les tests)
     */
    public void setCredentials(String username, String password) {
        if (usernameField != null) {
            usernameField.setText(username);
        }
        if (passwordField != null) {
            passwordField.setText(password);
        }
    }

    /**
     * Nettoie les champs de saisie
     */
    public void clearFields() {
        usernameField.clear();
        passwordField.clear();
        rememberMeCheckBox.setSelected(false);
        statusLabel.setText("Saisissez vos identifiants");
        usernameField.requestFocus();
    }
}