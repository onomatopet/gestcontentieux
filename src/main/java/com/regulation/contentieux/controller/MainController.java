package com.regulation.contentieux.controller;

import com.regulation.contentieux.service.AuthenticationService;
import com.regulation.contentieux.model.Utilisateur;
import com.regulation.contentieux.model.enums.RoleUtilisateur;
import com.regulation.contentieux.util.AlertUtil;
import com.regulation.contentieux.util.FXMLLoaderUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

/**
 * Contrôleur principal de l'application
 */
public class MainController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    // Menu et navigation
    @FXML private MenuBar menuBar;
    @FXML private Menu menuFichier;
    @FXML private Menu menuAffaires;
    @FXML private Menu menuContrevenants;
    @FXML private Menu menuAgents;
    @FXML private Menu menuEncaissements;
    @FXML private Menu menuRapports;
    @FXML private Menu menuAdministration;
    @FXML private Menu menuAide;

    // Barre de titre
    @FXML private Label titleLabel;
    @FXML private Label userInfoLabel;
    @FXML private Button logoutButton;

    // Barre d'outils
    @FXML private ToolBar toolBar;
    @FXML private Button newButton;
    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private Button refreshButton;
    @FXML private Button printButton;
    @FXML private Separator toolbarSeparator;
    @FXML private Button filterButton;
    @FXML private TextField searchField;

    // Zone principale
    @FXML private BorderPane contentPane;
    @FXML private StackPane mainContentArea;

    // Barre de statut
    @FXML private Label statusLabel;
    @FXML private Label connectionStatusLabel;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;

    // Services
    private AuthenticationService authService;
    private Utilisateur currentUser;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        authService = AuthenticationService.getInstance();
        currentUser = authService.getCurrentUser();

        if (currentUser == null) {
            logger.error("Aucun utilisateur connecté - redirection vers login");
            // Rediriger vers login
            return;
        }

        setupUI();
        setupMenus();
        setupToolbar();
        setupStatusBar();
        loadWelcomeView();

        logger.info("Interface principale initialisée pour: {}", currentUser.getUsername());
    }

    /**
     * Configuration initiale de l'interface
     */
    private void setupUI() {
        // Configuration du titre
        titleLabel.setText("Gestion des Affaires Contentieuses");

        // Information utilisateur
        updateUserInfo();

        // Bouton de déconnexion
        logoutButton.setGraphic(new FontIcon(Feather.LOG_OUT));
        logoutButton.setOnAction(e -> handleLogout());

        // Zone de contenu principal
        mainContentArea.setStyle("-fx-background-color: -fx-background;");

        // Barre de progression (cachée par défaut)
        progressBar.setVisible(false);
        progressLabel.setVisible(false);
    }

    /**
     * Configuration des menus selon les droits utilisateur
     */
    private void setupMenus() {
        // Menu Fichier
        addMenuItem(menuFichier, "Nouveau", Feather.PLUS, () -> handleNew());
        addMenuItem(menuFichier, "Ouvrir", Feather.FOLDER, () -> handleOpen());
        menuFichier.getItems().add(new SeparatorMenuItem());
        addMenuItem(menuFichier, "Importer", Feather.DOWNLOAD, () -> handleImport());
        addMenuItem(menuFichier, "Exporter", Feather.UPLOAD, () -> handleExport());
        menuFichier.getItems().add(new SeparatorMenuItem());
        addMenuItem(menuFichier, "Paramètres", Feather.SETTINGS, () -> handleSettings());
        menuFichier.getItems().add(new SeparatorMenuItem());
        addMenuItem(menuFichier, "Quitter", Feather.X, () -> handleExit());

        // Menu Affaires
        addMenuItem(menuAffaires, "Liste des affaires", Feather.LIST, () -> loadAffairesView());
        addMenuItem(menuAffaires, "Nouvelle affaire", Feather.PLUS_CIRCLE, () -> handleNewAffaire());
        menuAffaires.getItems().add(new SeparatorMenuItem());
        addMenuItem(menuAffaires, "Recherche avancée", Feather.SEARCH, () -> handleAdvancedSearch());

        // Menu Contrevenants
        addMenuItem(menuContrevenants, "Liste des contrevenants", Feather.USERS, () -> loadContrevenantsView());
        addMenuItem(menuContrevenants, "Nouveau contrevenant", Feather.USER_PLUS, () -> handleNewContrevenant());

        // Menu Agents
        if (currentUser.hasPermission(RoleUtilisateur.Permission.READ_AGENTS)) {
            addMenuItem(menuAgents, "Liste des agents", Feather.USER, () -> loadAgentsView());
            if (currentUser.hasPermission(RoleUtilisateur.Permission.WRITE_AGENTS)) {
                addMenuItem(menuAgents, "Nouvel agent", Feather.USER_PLUS, () -> handleNewAgent());
            }
        }

        // Menu Encaissements
        addMenuItem(menuEncaissements, "Liste des encaissements", Feather.DOLLAR_SIGN, () -> loadEncaissementsView());
        addMenuItem(menuEncaissements, "Nouvel encaissement", Feather.PLUS_CIRCLE, () -> handleNewEncaissement());
        menuEncaissements.getItems().add(new SeparatorMenuItem());
        addMenuItem(menuEncaissements, "Répartition des résultats", Feather.PIE_CHART, () -> loadRepartitionsView());

        // Menu Rapports
        addMenuItem(menuRapports, "Rapports de rétrocession", Feather.FILE_TEXT, () -> loadRapportsView());
        addMenuItem(menuRapports, "Statistiques", Feather.BAR_CHART_2, () -> loadStatistiquesView());
        menuRapports.getItems().add(new SeparatorMenuItem());
        addMenuItem(menuRapports, "Impression", Feather.PRINTER, () -> handlePrint());

        // Menu Administration (selon droits)
        if (currentUser.isAdmin()) {
            addMenuItem(menuAdministration, "Gestion des utilisateurs", Feather.USERS, () -> loadUsersView());
            addMenuItem(menuAdministration, "Référentiels", Feather.DATABASE, () -> loadReferentielsView());
            menuAdministration.getItems().add(new SeparatorMenuItem());
            addMenuItem(menuAdministration, "Synchronisation", Feather.REFRESH_CW, () -> handleSynchronization());

            if (currentUser.isSuperAdmin()) {
                addMenuItem(menuAdministration, "Logs système", Feather.FILE_TEXT, () -> loadLogsView());
                addMenuItem(menuAdministration, "Sauvegarde", Feather.SAVE, () -> handleBackup());
            }
        } else {
            menuAdministration.setVisible(false);
        }

        // Menu Aide
        addMenuItem(menuAide, "Manuel utilisateur", Feather.BOOK_OPEN, () -> showUserManual());
        addMenuItem(menuAide, "Support", Feather.HELP_CIRCLE, () -> showSupport());
        menuAide.getItems().add(new SeparatorMenuItem());
        addMenuItem(menuAide, "À propos", Feather.INFO, () -> showAbout());
    }

    /**
     * Configuration de la barre d'outils
     */
    private void setupToolbar() {
        // Boutons avec icônes
        newButton.setGraphic(new FontIcon(Feather.PLUS));
        editButton.setGraphic(new FontIcon(Feather.EDIT));
        deleteButton.setGraphic(new FontIcon(Feather.TRASH_2));
        refreshButton.setGraphic(new FontIcon(Feather.REFRESH_CW));
        printButton.setGraphic(new FontIcon(Feather.PRINTER));
        filterButton.setGraphic(new FontIcon(Feather.FILTER));

        // Actions
        newButton.setOnAction(e -> handleNew());
        editButton.setOnAction(e -> handleEdit());
        deleteButton.setOnAction(e -> handleDelete());
        refreshButton.setOnAction(e -> handleRefresh());
        printButton.setOnAction(e -> handlePrint());
        filterButton.setOnAction(e -> handleFilter());

        // Champ de recherche
        searchField.setPromptText("Rechercher...");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> handleSearch(newVal));

        // État initial
        updateToolbarState();
    }

    /**
     * Configuration de la barre de statut
     */
    private void setupStatusBar() {
        statusLabel.setText("Prêt");
        connectionStatusLabel.setText("Connecté (Local)");
        connectionStatusLabel.setStyle("-fx-text-fill: green;");

        // TODO: Vérifier connexion MySQL et mettre à jour
    }

    /**
     * Ajoute un élément de menu avec icône et action
     */
    private void addMenuItem(Menu menu, String text, Feather icon, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setGraphic(new FontIcon(icon));
        item.setOnAction(e -> action.run());
        menu.getItems().add(item);
    }

    /**
     * Met à jour les informations utilisateur
     */
    private void updateUserInfo() {
        String userText = String.format("%s (%s)",
                currentUser.getNomComplet(),
                currentUser.getRole().getDisplayName());

        if (currentUser.getLastLoginAt() != null) {
            userText += " - Dernière connexion: " +
                    currentUser.getLastLoginAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        }

        userInfoLabel.setText(userText);
    }

    /**
     * Met à jour l'état de la barre d'outils
     */
    private void updateToolbarState() {
        // État par défaut - sera mis à jour selon la vue courante
        editButton.setDisable(true);
        deleteButton.setDisable(true);
    }

    /**
     * Charge la vue d'accueil
     */
    private void loadWelcomeView() {
        try {
            Node welcomeView = FXMLLoaderUtil.loadParent("view/welcome.fxml");
            mainContentArea.getChildren().clear();
            mainContentArea.getChildren().add(welcomeView);
            titleLabel.setText("Accueil - Gestion des Affaires Contentieuses");
            statusLabel.setText("Bienvenue dans l'application");
        } catch (Exception e) {
            logger.error("Erreur lors du chargement de la vue d'accueil", e);
            showTemporaryWelcome();
        }
    }

    /**
     * Affiche un accueil temporaire
     */
    private void showTemporaryWelcome() {
        VBox welcome = new VBox(20);
        welcome.setStyle("-fx-alignment: center; -fx-padding: 50;");

        Label title = new Label("Bienvenue dans l'application");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        Label subtitle = new Label("Gestion des Affaires Contentieuses");
        subtitle.setStyle("-fx-font-size: 16px; -fx-text-fill: -fx-text-fill-secondary;");

        Label userWelcome = new Label("Connecté en tant que: " + currentUser.getNomComplet());
        userWelcome.setStyle("-fx-font-size: 14px;");

        welcome.getChildren().addAll(title, subtitle, userWelcome);

        mainContentArea.getChildren().clear();
        mainContentArea.getChildren().add(welcome);
    }

    // Actions des menus et boutons
    private void handleNew() { logger.info("Action: Nouveau"); }
    private void handleOpen() { logger.info("Action: Ouvrir"); }
    private void handleEdit() { logger.info("Action: Éditer"); }
    private void handleDelete() { logger.info("Action: Supprimer"); }
    private void handleRefresh() { logger.info("Action: Actualiser"); }
    private void handlePrint() { logger.info("Action: Imprimer"); }
    private void handleFilter() { logger.info("Action: Filtrer"); }
    private void handleSearch(String query) {
        if (!query.trim().isEmpty()) {
            logger.info("Recherche: {}", query);
        }
    }

    private void handleImport() { logger.info("Action: Importer"); }
    private void handleExport() { logger.info("Action: Exporter"); }
    private void handleSettings() { logger.info("Action: Paramètres"); }

    private void loadAffairesView() {
        titleLabel.setText("Affaires Contentieuses");
        statusLabel.setText("Chargement des affaires...");
        logger.info("Chargement de la vue des affaires");
    }

    private void handleNewAffaire() { logger.info("Action: Nouvelle affaire"); }
    private void handleAdvancedSearch() { logger.info("Action: Recherche avancée"); }

    private void loadContrevenantsView() {
        titleLabel.setText("Contrevenants");
        logger.info("Chargement de la vue des contrevenants");
    }
    private void handleNewContrevenant() { logger.info("Action: Nouveau contrevenant"); }

    private void loadAgentsView() {
        titleLabel.setText("Agents");
        logger.info("Chargement de la vue des agents");
    }
    private void handleNewAgent() { logger.info("Action: Nouvel agent"); }

    private void loadEncaissementsView() {
        titleLabel.setText("Encaissements");
        logger.info("Chargement de la vue des encaissements");
    }
    private void handleNewEncaissement() { logger.info("Action: Nouvel encaissement"); }

    private void loadRepartitionsView() {
        titleLabel.setText("Répartition des résultats");
        logger.info("Chargement de la vue des répartitions");
    }

    private void loadRapportsView() {
        titleLabel.setText("Rapports");
        logger.info("Chargement de la vue des rapports");
    }

    private void loadStatistiquesView() {
        titleLabel.setText("Statistiques");
        logger.info("Chargement de la vue des statistiques");
    }

    private void loadUsersView() {
        titleLabel.setText("Gestion des utilisateurs");
        logger.info("Chargement de la vue des utilisateurs");
    }

    private void loadReferentielsView() {
        titleLabel.setText("Référentiels");
        logger.info("Chargement de la vue des référentiels");
    }

    private void handleSynchronization() { logger.info("Action: Synchronisation"); }
    private void loadLogsView() {
        titleLabel.setText("Logs système");
        logger.info("Chargement de la vue des logs");
    }
    private void handleBackup() { logger.info("Action: Sauvegarde"); }

    private void showUserManual() {
        AlertUtil.showInfoAlert("Manuel utilisateur",
                "Aide",
                "Le manuel utilisateur sera disponible prochainement.");
    }

    private void showSupport() {
        AlertUtil.showInfoAlert("Support",
                "Contactez le support",
                "Pour obtenir de l'aide, contactez votre administrateur système.");
    }

    private void showAbout() {
        AlertUtil.showInfoAlert("À propos",
                "Gestion des Affaires Contentieuses v1.0.0",
                "Application développée pour la gestion des affaires contentieuses.\n\n" +
                        "© 2024 Regulation Team");
    }

    private void handleExit() {
        if (AlertUtil.showConfirmAlert("Quitter",
                "Confirmer la fermeture",
                "Voulez-vous vraiment quitter l'application ?")) {
            Platform.exit();
        }
    }

    private void handleLogout() {
        if (AlertUtil.showConfirmAlert("Déconnexion",
                "Confirmer la déconnexion",
                "Voulez-vous vraiment vous déconnecter ?")) {

            authService.logout();

            // Rediriger vers l'écran de connexion
            try {
                Stage stage = (Stage) logoutButton.getScene().getWindow();
                stage.setScene(FXMLLoaderUtil.loadScene("view/login.fxml", 400, 350));
                stage.setMaximized(false);
                stage.setWidth(400);
                stage.setHeight(350);
                stage.centerOnScreen();

                logger.info("Redirection vers l'écran de connexion");
            } catch (Exception e) {
                logger.error("Erreur lors de la redirection vers login", e);
                Platform.exit();
            }
        }
    }

    /**
     * Met à jour la barre de progression
     */
    public void updateProgress(double progress, String message) {
        Platform.runLater(() -> {
            if (progress < 0) {
                progressBar.setVisible(false);
                progressLabel.setVisible(false);
            } else {
                progressBar.setVisible(true);
                progressLabel.setVisible(true);
                progressBar.setProgress(progress);
                progressLabel.setText(message);
            }
        });
    }

    /**
     * Met à jour le statut de connexion
     */
    public void updateConnectionStatus(boolean isConnected, boolean isMySQLAvailable) {
        Platform.runLater(() -> {
            if (isMySQLAvailable) {
                connectionStatusLabel.setText("Connecté (Local + Distant)");
                connectionStatusLabel.setStyle("-fx-text-fill: green;");
            } else if (isConnected) {
                connectionStatusLabel.setText("Connecté (Local uniquement)");
                connectionStatusLabel.setStyle("-fx-text-fill: orange;");
            } else {
                connectionStatusLabel.setText("Déconnecté");
                connectionStatusLabel.setStyle("-fx-text-fill: red;");
            }
        });
    }

    /**
     * Met à jour la barre de statut
     */
    public void updateStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    /**
     * Charge une vue dans la zone de contenu principale
     */
    public void loadView(String fxmlPath, String title) {
        try {
            updateProgress(0.3, "Chargement de la vue...");

            Node view = FXMLLoaderUtil.loadParent(fxmlPath);

            Platform.runLater(() -> {
                mainContentArea.getChildren().clear();
                mainContentArea.getChildren().add(view);
                titleLabel.setText(title);
                updateProgress(-1, "");
                updateStatus("Vue chargée: " + title);
            });

        } catch (Exception e) {
            logger.error("Erreur lors du chargement de la vue: " + fxmlPath, e);
            updateProgress(-1, "");
            updateStatus("Erreur de chargement");

            AlertUtil.showErrorAlert("Erreur de chargement",
                    "Impossible de charger la vue",
                    "Erreur technique: " + e.getMessage());
        }
    }

    /**
     * Active/désactive les boutons selon les permissions
     */
    public void updateButtonStates(boolean canEdit, boolean canDelete, boolean canCreate) {
        Platform.runLater(() -> {
            editButton.setDisable(!canEdit);
            deleteButton.setDisable(!canDelete);
            newButton.setDisable(!canCreate);
        });
    }
}