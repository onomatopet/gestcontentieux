package com.regulation.contentieux.controller;

import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.regulation.contentieux.model.enums.RoleUtilisateur;
import com.regulation.contentieux.service.AuthenticationService;
import com.regulation.contentieux.util.StageManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

/**
 * Contrôleur principal de l'application
 * Gère la navigation et l'interface principale
 */
public class MainController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML private BorderPane mainBorderPane;
    @FXML private VBox menuVBox;
    @FXML private Label userLabel;
    @FXML private Label roleLabel;
    @FXML private Label dateTimeLabel;

    // Menus principaux
    @FXML private TitledPane affairesMenu;
    @FXML private TitledPane agentsMenu;
    @FXML private TitledPane contrevenantsMenu;
    @FXML private TitledPane encaissementsMenu;
    @FXML private TitledPane rapportsMenu;
    @FXML private TitledPane administrationMenu;

    // Boutons de menu
    @FXML private Button btnNouvelleAffaire;
    @FXML private Button btnListeAffaires;
    @FXML private Button btnNouvelAgent;
    @FXML private Button btnListeAgents;
    @FXML private Button btnNouveauContrevenant;
    @FXML private Button btnListeContrevenants;
    @FXML private Button btnNouvelEncaissement;
    @FXML private Button btnListeEncaissements;
    @FXML private Button btnRapportRepartition;
    @FXML private Button btnTableauAmendes;
    @FXML private Button btnGestionUtilisateurs;
    @FXML private Button btnReferentiel;
    @FXML private Button btnParametres;

    private final AuthenticationService authService = AuthenticationService.getInstance();
    private final StageManager stageManager = StageManager.getInstance();

    // Référence à la fenêtre principale
    private Stage currentStage;

    // Titre de l'application
    private static final String APP_TITLE = "Gestion des Affaires Contentieuses";

    // Méthode pour définir le stage actuel (à ajouter dans MainController)
    public void setCurrentStage(Stage stage) {
        this.currentStage = stage;
    }

    // Méthode pour obtenir le stage actuel (à ajouter dans MainController)
    public Stage getCurrentStage() {
        return currentStage;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupUserInfo();
        setupMenuAccess();
        setupDateTime();
        loadDefaultView();
    }

    /**
     * Configure les informations utilisateur
     */
    private void setupUserInfo() {
        if (authService.isAuthenticated()) {
            userLabel.setText(authService.getCurrentUser().getDisplayName());
            roleLabel.setText(authService.getCurrentUser().getRole().getLibelle());
        }
    }

    /**
     * Configure l'accès aux menus selon les permissions
     */
    private void setupMenuAccess() {
        // Gestion des affaires
        boolean canManageAffaires = authService.hasPermission(RoleUtilisateur.Permission.GESTION_AFFAIRES);
        affairesMenu.setDisable(!canManageAffaires);

        // Gestion des agents
        boolean canManageAgents = authService.hasPermission(RoleUtilisateur.Permission.GESTION_AGENTS);
        agentsMenu.setDisable(!canManageAgents);

        // Gestion des contrevenants
        boolean canManageContrevenants = authService.hasPermission(RoleUtilisateur.Permission.GESTION_CONTREVENANTS);
        contrevenantsMenu.setDisable(!canManageContrevenants);

        // Gestion des encaissements
        boolean canManageEncaissements = authService.hasPermission(RoleUtilisateur.Permission.GESTION_ENCAISSEMENTS);
        encaissementsMenu.setDisable(!canManageEncaissements);

        // Génération des rapports
        boolean canGenerateRapports = authService.hasPermission(RoleUtilisateur.Permission.GENERATION_RAPPORTS);
        rapportsMenu.setDisable(!canGenerateRapports);

        // Administration
        boolean canManageUsers = authService.hasPermission(RoleUtilisateur.Permission.GESTION_UTILISATEURS);
        boolean canManageReferentiel = authService.hasPermission(RoleUtilisateur.Permission.GESTION_REFERENTIEL);
        administrationMenu.setDisable(!canManageUsers && !canManageReferentiel);

        // Boutons spécifiques d'administration
        btnGestionUtilisateurs.setDisable(!canManageUsers);
        btnReferentiel.setDisable(!canManageReferentiel);
    }

    /**
     * Configure l'affichage de la date et heure
     */
    private void setupDateTime() {
        // Mise à jour de l'heure toutes les secondes
        Thread dateTimeThread = new Thread(() -> {
            while (true) {
                Platform.runLater(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE dd MMMM yyyy - HH:mm:ss");
                    dateTimeLabel.setText(now.format(formatter));
                });

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        dateTimeThread.setDaemon(true);
        dateTimeThread.start();
    }

    // Méthode pour mettre à jour le titre de la fenêtre (exemple d'utilisation)
    public void updateWindowTitle(String subtitle) {
        if (currentStage != null) {
            if (subtitle != null && !subtitle.isEmpty()) {
                currentStage.setTitle(APP_TITLE + " - " + subtitle);
            } else {
                currentStage.setTitle(APP_TITLE);
            }
        }
    }

    /**
     * Charge la vue par défaut
     */
    private void loadDefaultView() {
        try {
            loadView("/fxml/dashboard.fxml");
        } catch (Exception e) {
            logger.warn("Impossible de charger le dashboard, chargement de la liste des affaires");
            if (authService.hasPermission(RoleUtilisateur.Permission.GESTION_AFFAIRES)) {
                onListeAffaires();
            }
        }
    }

    /**
     * Charge une vue dans le panneau central
     */
    private void loadView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node view = loader.load();
            mainBorderPane.setCenter(view);
            logger.debug("Vue chargée: {}", fxmlPath);
        } catch (IOException e) {
            logger.error("Erreur lors du chargement de la vue: " + fxmlPath, e);
            showErrorAlert("Erreur de chargement",
                    "Impossible de charger la vue demandée.");
        }
    }

    /**
     * Charge une vue avec titre (surcharge pour WelcomeController)
     */
    public void loadView(String fxmlPath, String title) {
        loadView(fxmlPath);
        // Le titre pourrait être utilisé pour mettre à jour un label dans l'interface
        if (title != null && currentStage != null) {
            currentStage.setTitle(APP_TITLE + " - " + title);
        }
    }

    // ==================== GESTIONNAIRES D'ÉVÉNEMENTS ====================

    // Menu Affaires
    @FXML
    private void onNouvelleAffaire() {
        loadView("/fxml/affaire/affaire-form.fxml");
    }

    @FXML
    private void onListeAffaires() {
        loadView("/fxml/affaire/affaire-list.fxml");
    }

    // Menu Agents
    @FXML
    private void onNouvelAgent() {
        loadView("/fxml/agent/agent-form.fxml");
    }

    @FXML
    private void onListeAgents() {
        loadView("/fxml/agent/agent-list.fxml");
    }

    // Menu Contrevenants
    @FXML
    private void onNouveauContrevenant() {
        loadView("/fxml/contrevenant/contrevenant-form.fxml");
    }

    @FXML
    private void onListeContrevenants() {
        loadView("/fxml/contrevenant/contrevenant-list.fxml");
    }

    // Menu Encaissements
    @FXML
    private void onNouvelEncaissement() {
        loadView("/fxml/encaissement/encaissement-form.fxml");
    }

    @FXML
    private void onListeEncaissements() {
        loadView("/fxml/encaissement/encaissement-list.fxml");
    }

    // Menu Rapports
    @FXML
    private void onRapportRepartition() {
        loadView("/fxml/rapport/rapport-repartition.fxml");
    }

    @FXML
    private void onTableauAmendes() {
        loadView("/fxml/rapport/tableau-amendes.fxml");
    }

    // Menu Administration
    @FXML
    private void onGestionUtilisateurs() {
        loadView("/fxml/admin/user-management.fxml");
    }

    @FXML
    private void onReferentiel() {
        loadView("/fxml/admin/referentiel.fxml");
    }

    @FXML
    private void onParametres() {
        loadView("/fxml/admin/parametres.fxml");
    }

    // Actions utilisateur
    @FXML
    private void onChangePassword() {
        try {
            stageManager.showChangePasswordDialog();
        } catch (Exception e) {
            logger.error("Erreur lors de l'ouverture du changement de mot de passe", e);
            showErrorAlert("Erreur", "Impossible d'ouvrir la fenêtre de changement de mot de passe.");
        }
    }

    @FXML
    private void onLogout() {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirmation");
        confirmAlert.setHeaderText("Déconnexion");
        confirmAlert.setContentText("Voulez-vous vraiment vous déconnecter ?");

        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                authService.logout();
                stageManager.switchToLogin();
            }
        });
    }

    @FXML
    private void onAbout() {
        Alert aboutAlert = new Alert(Alert.AlertType.INFORMATION);
        aboutAlert.setTitle("À propos");
        aboutAlert.setHeaderText("Gestion des Affaires Contentieuses");
        aboutAlert.setContentText(
                "Version 1.0.0\n\n" +
                        "Application de gestion des affaires contentieuses\n" +
                        "Direction de la Régulation\n\n" +
                        "© 2024 - Tous droits réservés"
        );
        aboutAlert.showAndWait();
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    /**
     * Affiche une alerte d'erreur
     */
    private void showErrorAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Recharge la vue actuelle
     */
    public void refreshCurrentView() {
        Node currentView = mainBorderPane.getCenter();
        if (currentView != null) {
            logger.debug("Actualisation de la vue courante");
            // Forcer le rafraîchissement
            mainBorderPane.setCenter(null);
            mainBorderPane.setCenter(currentView);
        }
    }

    /**
     * Obtient le contrôleur de la vue actuelle
     */
    public Object getCurrentViewController() {
        Node currentView = mainBorderPane.getCenter();
        if (currentView != null && currentView.getUserData() instanceof FXMLLoader) {
            FXMLLoader loader = (FXMLLoader) currentView.getUserData();
            return loader.getController();
        }
        return null;
    }
}