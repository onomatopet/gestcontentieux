package com.regulation.contentieux.controller;

import com.regulation.contentieux.service.AuthenticationService;
import com.regulation.contentieux.model.Utilisateur;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Contrôleur pour la vue d'accueil
 */
public class WelcomeController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(WelcomeController.class);

    // Labels d'information utilisateur
    @FXML private Label welcomeUserLabel;

    // Compteurs
    @FXML private Label affairesCountLabel;
    @FXML private Label contrevenantsCountLabel;
    @FXML private Label encaissementsAmountLabel;

    // Boutons de navigation
    @FXML private Button viewAffairesButton;
    @FXML private Button viewContrevenantsButton;
    @FXML private Button viewEncaissementsButton;

    // Actions rapides
    @FXML private Button newAffaireButton;
    @FXML private Button newContrevenantButton;
    @FXML private Button newEncaissementButton;

    // Rapports
    @FXML private Button rapportRepartitionButton;
    @FXML private Button rapportStatistiquesButton;
    @FXML private Button printReportsButton;

    // Administration
    @FXML private VBox adminCard;
    @FXML private Button manageUsersButton;
    @FXML private Button manageReferentielsButton;
    @FXML private Button synchronizeButton;

    // Informations système
    @FXML private Label dbStatusLabel;
    @FXML private Label lastSyncLabel;
    @FXML private Label versionLabel;

    // Services
    private AuthenticationService authService;
    private Utilisateur currentUser;
    private NumberFormat currencyFormat;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        authService = AuthenticationService.getInstance();
        currentUser = authService.getCurrentUser();

        // Configuration du format de devise (FCFA)
        currencyFormat = NumberFormat.getInstance(Locale.FRANCE);

        setupUI();
        setupActions();
        loadStatistics();

        logger.info("Vue d'accueil initialisée pour: {}",
                currentUser != null ? currentUser.getUsername() : "utilisateur inconnu");
    }

    /**
     * Configuration initiale de l'interface
     */
    private void setupUI() {
        if (currentUser != null) {
            welcomeUserLabel.setText("Connecté en tant que: " + currentUser.getNomComplet() +
                    " (" + currentUser.getRole().getDisplayName() + ")");

            // Afficher la carte administration si l'utilisateur est admin
            if (currentUser.isAdmin()) {
                adminCard.setVisible(true);
                adminCard.setManaged(true);
            }
        }

        // Configuration des informations système
        versionLabel.setText("Version: 1.0.0");
        dbStatusLabel.setText("Base de données: Vérification...");
        lastSyncLabel.setText("Dernière synchronisation: Vérification...");
    }

    /**
     * Configuration des actions des boutons
     */
    private void setupActions() {
        // Navigation vers les vues principales
        viewAffairesButton.setOnAction(e -> navigateToAffaires());
        viewContrevenantsButton.setOnAction(e -> navigateToContrevenants());
        viewEncaissementsButton.setOnAction(e -> navigateToEncaissements());

        // Actions rapides de création
        newAffaireButton.setOnAction(e -> createNewAffaire());
        newContrevenantButton.setOnAction(e -> createNewContrevenant());
        newEncaissementButton.setOnAction(e -> createNewEncaissement());

        // Rapports
        rapportRepartitionButton.setOnAction(e -> openRapportRepartition());
        rapportStatistiquesButton.setOnAction(e -> openStatistiques());
        printReportsButton.setOnAction(e -> openPrintReports());

        // Administration (si visible)
        if (adminCard.isVisible()) {
            manageUsersButton.setOnAction(e -> openUserManagement());
            manageReferentielsButton.setOnAction(e -> openReferentiels());
            synchronizeButton.setOnAction(e -> synchronizeData());
        }
    }

    /**
     * Charge les statistiques en arrière-plan
     */
    private void loadStatistics() {
        Task<Void> statsTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    // Simulation du chargement des statistiques
                    // TODO: Remplacer par de vraies requêtes à la base

                    Thread.sleep(1000); // Simulation du temps de chargement

                    // Statistiques simulées
                    int affairesCount = 125;
                    int contrevenantsCount = 89;
                    double encaissementsAmount = 15750000.0; // 15,750,000 FCFA

                    Platform.runLater(() -> {
                        affairesCountLabel.setText(String.valueOf(affairesCount));
                        contrevenantsCountLabel.setText(String.valueOf(contrevenantsCount));
                        encaissementsAmountLabel.setText(formatCurrency(encaissementsAmount) + " FCFA");
                    });

                    // Statut de la base de données
                    Platform.runLater(() -> {
                        dbStatusLabel.setText("Base de données: Connectée (SQLite)");
                        lastSyncLabel.setText("Dernière synchronisation: Jamais");
                    });

                } catch (Exception e) {
                    logger.error("Erreur lors du chargement des statistiques", e);
                    Platform.runLater(() -> {
                        affairesCountLabel.setText("Erreur");
                        contrevenantsCountLabel.setText("Erreur");
                        encaissementsAmountLabel.setText("Erreur");
                        dbStatusLabel.setText("Base de données: Erreur");
                    });
                }
                return null;
            }
        };

        Thread statsThread = new Thread(statsTask);
        statsThread.setDaemon(true);
        statsThread.start();
    }

    /**
     * Formate un montant en devise locale
     */
    private String formatCurrency(double amount) {
        return currencyFormat.format(amount);
    }

    // Actions de navigation
    private void navigateToAffaires() {
        logger.info("Navigation vers la liste des affaires");
        findMainController().ifPresent(controller ->
                controller.loadView("view/affaire-list.fxml", "Liste des Affaires"));
    }

    private void navigateToContrevenants() {
        logger.info("Navigation vers la liste des contrevenants");
        findMainController().ifPresent(controller ->
                controller.loadView("view/contrevenant-list.fxml", "Liste des Contrevenants"));
    }

    private void navigateToEncaissements() {
        logger.info("Navigation vers la liste des encaissements");
        findMainController().ifPresent(controller ->
                controller.loadView("view/encaissement-list.fxml", "Liste des Encaissements"));
    }

    // Actions de création
    private void createNewAffaire() {
        logger.info("Création d'une nouvelle affaire");
        // TODO: Ouvrir le formulaire de création d'affaire
        showNotImplemented("Création d'affaire");
    }

    private void createNewContrevenant() {
        logger.info("Création d'un nouveau contrevenant");
        // TODO: Ouvrir le formulaire de création de contrevenant
        showNotImplemented("Création de contrevenant");
    }

    private void createNewEncaissement() {
        logger.info("Création d'un nouvel encaissement");
        // TODO: Ouvrir le formulaire de création d'encaissement
        showNotImplemented("Création d'encaissement");
    }

    // Actions de rapports
    private void openRapportRepartition() {
        logger.info("Ouverture du rapport de rétrocession");
        findMainController().ifPresent(controller ->
                controller.loadView("view/rapport-repartition.fxml", "Rapport de Rétrocession"));
    }

    private void openStatistiques() {
        logger.info("Ouverture des statistiques");
        findMainController().ifPresent(controller ->
                controller.loadView("view/statistiques.fxml", "Statistiques"));
    }

    private void openPrintReports() {
        logger.info("Ouverture de l'interface d'impression");
        showNotImplemented("Interface d'impression");
    }

    // Actions d'administration
    private void openUserManagement() {
        logger.info("Ouverture de la gestion des utilisateurs");
        findMainController().ifPresent(controller ->
                controller.loadView("view/user-management.fxml", "Gestion des Utilisateurs"));
    }

    private void openReferentiels() {
        logger.info("Ouverture des référentiels");
        findMainController().ifPresent(controller ->
                controller.loadView("view/referentiels.fxml", "Gestion des Référentiels"));
    }

    private void synchronizeData() {
        logger.info("Synchronisation des données");

        // Désactiver le bouton pendant la synchronisation
        synchronizeButton.setDisable(true);
        synchronizeButton.setText("Synchronisation...");

        Task<Void> syncTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                // TODO: Implémenter la vraie synchronisation
                Thread.sleep(3000); // Simulation
                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    synchronizeButton.setDisable(false);
                    synchronizeButton.setText("Synchroniser");
                    lastSyncLabel.setText("Dernière synchronisation: Maintenant");

                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Synchronisation");
                    alert.setHeaderText("Synchronisation terminée");
                    alert.setContentText("Les données ont été synchronisées avec succès.");
                    alert.showAndWait();
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    synchronizeButton.setDisable(false);
                    synchronizeButton.setText("Synchroniser");

                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Erreur de synchronisation");
                    alert.setHeaderText("Synchronisation échouée");
                    alert.setContentText("Une erreur s'est produite lors de la synchronisation.");
                    alert.showAndWait();
                });
            }
        };

        Thread syncThread = new Thread(syncTask);
        syncThread.setDaemon(true);
        syncThread.start();
    }

    /**
     * Trouve le contrôleur principal
     */
    private java.util.Optional<MainController> findMainController() {
        try {
            // Récupérer le contrôleur principal via la hiérarchie des vues
            // Cette méthode peut être améliorée avec un système de communication
            // Pour l'instant, on retourne empty pour éviter les erreurs
            return java.util.Optional.empty();
        } catch (Exception e) {
            logger.error("Impossible de trouver le contrôleur principal", e);
            return java.util.Optional.empty();
        }
    }

    /**
     * Affiche un message pour les fonctionnalités non encore implémentées
     */
    private void showNotImplemented(String feature) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Fonctionnalité en développement");
        alert.setHeaderText(feature + " - En cours de développement");
        alert.setContentText("Cette fonctionnalité sera disponible dans une prochaine version.");
        alert.showAndWait();
    }

    /**
     * Actualise les statistiques affichées
     */
    public void refreshStatistics() {
        loadStatistics();
    }

    /**
     * Met à jour le statut de connexion à la base de données
     */
    public void updateDatabaseStatus(boolean isConnected, boolean isMySQLAvailable) {
        Platform.runLater(() -> {
            if (isMySQLAvailable) {
                dbStatusLabel.setText("Base de données: Connectée (SQLite + MySQL)");
            } else if (isConnected) {
                dbStatusLabel.setText("Base de données: Connectée (SQLite uniquement)");
            } else {
                dbStatusLabel.setText("Base de données: Déconnectée");
            }
        });
    }

    /**
     * Met à jour la dernière synchronisation
     */
    public void updateLastSync(String syncTime) {
        Platform.runLater(() ->
                lastSyncLabel.setText("Dernière synchronisation: " + syncTime));
    }
}