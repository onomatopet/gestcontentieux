package com.regulation.contentieux.controller;

import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Imports model corrigés
import com.regulation.contentieux.model.Affaire;
import com.regulation.contentieux.model.Contrevenant;
import com.regulation.contentieux.model.Agent;
import com.regulation.contentieux.model.Encaissement;
import com.regulation.contentieux.model.Mandat;
import com.regulation.contentieux.model.enums.RoleUtilisateur;

import com.regulation.contentieux.service.MandatService;
import com.regulation.contentieux.service.AuthenticationService;
import com.regulation.contentieux.util.StageManager;
import com.regulation.contentieux.util.AlertUtil;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCombination;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.collections.ObservableList;
import javafx.util.Duration;
import javafx.stage.Modality;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.math.BigDecimal;

/**
 * Contrôleur principal de l'application
 * CORRIGÉ pour correspondre au fichier main.fxml
 */
public class MainController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    // Composants principaux du FXML - CORRIGÉS selon le vrai main.fxml
    @FXML private BorderPane contentPane;      // au lieu de mainBorderPane
    @FXML private StackPane mainContentArea;   // zone de contenu principal
    @FXML private MenuBar menuBar;
    @FXML private ToolBar toolBar;

    // Variables FXML manquantes ajoutées
    @FXML private MenuBar mainMenuBar;
    @FXML private ToolBar mainToolBar;
    @FXML private Button newAffaireButton;

    // Labels et boutons présents dans le FXML
    @FXML private Label titleLabel;
    @FXML private Label userInfoLabel;
    @FXML private Label statusLabel;           // dans la barre de statut
    @FXML private Label connectionStatusLabel; // dans la barre de statut
    @FXML private Label progressLabel;         // dans la barre de statut
    @FXML private ProgressBar progressBar;     // dans la barre de statut

    @FXML private Menu fileMenu;  // Menu Fichier
    @FXML private Label mandatLabel;  // Label dans la barre de statut pour afficher le mandat actif
    @FXML private MenuItem newAffaireMenuItem;  // Item de menu Nouvelle Affaire
    @FXML private MenuItem newEncaissementMenuItem;  // Item de menu Nouvel Encaissement

    @FXML private Button logoutButton;
    @FXML private Button newButton;
    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private Button refreshButton;
    @FXML private Button printButton;
    @FXML private Button filterButton;
    @FXML private TextField searchField;

    // Menus présents dans le FXML
    @FXML private Menu menuFichier;
    @FXML private Menu menuAffaires;
    @FXML private Menu menuContrevenants;
    @FXML private Menu menuAgents;
    @FXML private Menu menuEncaissements;
    @FXML private Menu menuRapports;
    @FXML private Menu menuAdministration;
    @FXML private Menu menuAide;

    // Services
    private final AuthenticationService authService = AuthenticationService.getInstance();
    private final StageManager stageManager = StageManager.getInstance();

    // Référence à la fenêtre principale
    private Stage currentStage;

    // Contrôleur de bienvenue (ajouté pour résoudre l'erreur)
    private WelcomeController welcomeController;

    // Variables pour la recherche et les tâches asynchrones
    private Timeline searchTask;
    private String lastSearchText = "";

    // Titre de l'application
    private static final String APP_TITLE = "Gestion des Affaires Contentieuses";

    public void setCurrentStage(Stage stage) {
        this.currentStage = stage;
    }

    public Stage getCurrentStage() {
        return currentStage;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initialisation du MainController");

        setupUserInfo();
        setupMenuAccess();
        setupEventHandlers();
        loadDefaultView();

        logger.info("MainController initialisé avec succès");

        // Ajouter l'item de menu pour la gestion des mandats
        setupMandatMenuItem();

        // Raccourci clavier global pour ouvrir la gestion des mandats
        // Utilisation de Platform.runLater pour attendre que la scène soit prête
        Platform.runLater(() -> {
            Scene scene = getCurrentScene();
            if (scene != null) {
                scene.getAccelerators().put(
                        KeyCombination.keyCombination("Ctrl+M"),
                        () -> openMandatManagement()
                );
            }
        });

        // Mettre à jour l'affichage du mandat actif
        updateMandatActif();

        // Configurer la mise à jour périodique du mandat actif (optionnel)
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(30), e -> updateMandatActif()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void setupWindowFocusListener() {
        // Attendre que la scène soit prête
        Platform.runLater(() -> {
            Scene scene = getCurrentScene();
            if (scene != null && scene.getWindow() instanceof Stage) {
                Stage primaryStage = (Stage) scene.getWindow();
                primaryStage.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                    if (isFocused && !wasFocused) {
                        // La fenêtre vient de reprendre le focus
                        updateMandatActif();
                    }
                });
            }
        });
    }

    private void addMandatButtonToToolbar() {
        if (mainToolBar != null) {
            // Créer un bouton pour la gestion des mandats
            Button mandatButton = new Button();
            mandatButton.setTooltip(new Tooltip("Gestion des Mandats (Ctrl+M)"));

            // Ajouter une icône
            try {
                ImageView icon = new ImageView(new Image(getClass().getResourceAsStream("/images/mandat-icon.png")));
                icon.setFitWidth(16);
                icon.setFitHeight(16);
                mandatButton.setGraphic(icon);
            } catch (Exception e) {
                logger.debug("Icône mandat non trouvée");
            }

            // Ajouter l'action
            mandatButton.setOnAction(e -> openMandatManagement());

            // Trouver la position après le séparateur ou à la fin
            ObservableList<Node> items = mainToolBar.getItems();
            int insertIndex = -1;

            // Chercher un séparateur après les boutons principaux
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) instanceof Separator) {
                    insertIndex = i + 1;
                    break;
                }
            }

            // Si pas de séparateur trouvé, ajouter à la fin
            if (insertIndex == -1) {
                items.add(new Separator());
                items.add(mandatButton);
            } else {
                items.add(insertIndex, mandatButton);
            }

            logger.debug("Bouton Mandat ajouté à la barre d'outils");
        }
    }

    /**
     * Configure les informations utilisateur
     */
    private void setupUserInfo() {
        if (authService.isAuthenticated() && userInfoLabel != null) {
            var user = authService.getCurrentUser();
            userInfoLabel.setText(user.getDisplayName() + " (" + user.getRole().getDisplayName() + ")");
            logger.debug("Informations utilisateur configurées: {}", user.getDisplayName());
        }
    }

    /**
     * Configure l'accès aux menus selon les droits
     */
    private void setupMenuAccess() {
        if (!authService.isAuthenticated()) return;

        var user = authService.getCurrentUser();
        var role = user.getRole();

        // Configuration des accès selon le rôle
        if (menuAffaires != null) {
            boolean affairesAccess = role == RoleUtilisateur.SUPER_ADMIN ||
                    role == RoleUtilisateur.ADMINISTRATEUR ||
                    role == RoleUtilisateur.CHEF_SERVICE ||
                    role == RoleUtilisateur.AGENT_SAISIE;
            menuAffaires.setVisible(affairesAccess);
            logger.debug("Menu Affaires - Accès: {}", affairesAccess);
        }

        if (menuAgents != null) {
            boolean agentsAccess = role == RoleUtilisateur.SUPER_ADMIN ||
                    role == RoleUtilisateur.ADMINISTRATEUR ||
                    role == RoleUtilisateur.CHEF_SERVICE;
            menuAgents.setVisible(agentsAccess);
            logger.debug("Menu Agents - Accès: {}", agentsAccess);
        }

        if (menuContrevenants != null) {
            menuContrevenants.setVisible(true); // Accessible à tous
            logger.debug("Menu Contrevenants - Accès: true");
        }

        if (menuEncaissements != null) {
            boolean encaissementsAccess = role == RoleUtilisateur.SUPER_ADMIN ||
                    role == RoleUtilisateur.ADMINISTRATEUR ||
                    role == RoleUtilisateur.CHEF_SERVICE ||
                    role == RoleUtilisateur.AGENT_SAISIE;
            menuEncaissements.setVisible(encaissementsAccess);
            logger.debug("Menu Encaissements - Accès: {}", encaissementsAccess);
        }

        if (menuRapports != null) {
            boolean rapportsAccess = role == RoleUtilisateur.SUPER_ADMIN ||
                    role == RoleUtilisateur.ADMINISTRATEUR ||
                    role == RoleUtilisateur.CHEF_SERVICE;
            menuRapports.setVisible(rapportsAccess);
            logger.debug("Menu Rapports - Accès: {}", rapportsAccess);
        }

        if (menuAdministration != null) {
            boolean adminAccess = role == RoleUtilisateur.SUPER_ADMIN || role == RoleUtilisateur.ADMINISTRATEUR;
            menuAdministration.setVisible(adminAccess);
            logger.debug("Menu Administration - Accès: {}", adminAccess);
        }
    }

    private void setupMandatMenuItem() {
        // Ajouter un item de menu pour la gestion des mandats dans le menu Fichier
        if (fileMenu != null || menuFichier != null) {
            Menu targetMenu = fileMenu != null ? fileMenu : menuFichier;

            MenuItem mandatMenuItem = new MenuItem("Gestion des Mandats");
            mandatMenuItem.setAccelerator(KeyCombination.keyCombination("Ctrl+M"));
            mandatMenuItem.setOnAction(e -> openMandatManagement());

            // Ajouter une icône si disponible
            try {
                ImageView icon = new ImageView(new Image(getClass().getResourceAsStream("/images/mandat-icon.png")));
                icon.setFitWidth(16);
                icon.setFitHeight(16);
                mandatMenuItem.setGraphic(icon);
            } catch (Exception e) {
                logger.debug("Icône mandat non trouvée");
            }

            // Insérer après "Nouveau" ou au début
            ObservableList<MenuItem> items = targetMenu.getItems();
            int insertIndex = 0;

            // Chercher l'item "Nouveau" ou "Quitter"
            for (int i = 0; i < items.size(); i++) {
                MenuItem item = items.get(i);
                if (item.getText() != null &&
                        (item.getText().toLowerCase().contains("nouveau") ||
                                item.getText().toLowerCase().contains("quitter"))) {
                    insertIndex = i + 1;
                    break;
                }
            }

            // Ajouter un séparateur si nécessaire
            if (insertIndex > 0 && insertIndex < items.size()) {
                items.add(insertIndex, mandatMenuItem);
                // Ajouter un séparateur après si l'élément suivant n'en est pas un
                if (insertIndex + 1 < items.size() &&
                        !(items.get(insertIndex + 1) instanceof SeparatorMenuItem)) {
                    items.add(insertIndex + 1, new SeparatorMenuItem());
                }
            } else {
                // Si on ne trouve pas où l'insérer, l'ajouter à la fin
                if (!items.isEmpty() && !(items.get(items.size() - 1) instanceof SeparatorMenuItem)) {
                    items.add(new SeparatorMenuItem());
                }
                items.add(mandatMenuItem);
            }

            logger.info("Menu Gestion des Mandats ajouté");
        } else {
            logger.warn("Menu Fichier non trouvé - Impossible d'ajouter le menu Mandat");
        }
    }

    /**
     * Configure les gestionnaires d'événements
     */
    private void setupEventHandlers() {
        if (logoutButton != null) {
            logoutButton.setOnAction(e -> onLogout());
        }

        if (newButton != null) {
            newButton.setOnAction(e -> onNewAction());
        }

        if (editButton != null) {
            editButton.setOnAction(e -> onEditAction());
        }

        if (deleteButton != null) {
            deleteButton.setOnAction(e -> onDeleteAction());
        }

        if (refreshButton != null) {
            refreshButton.setOnAction(e -> onRefreshAction());
        }

        if (printButton != null) {
            printButton.setOnAction(e -> onPrintAction());
        }

        if (filterButton != null) {
            filterButton.setOnAction(e -> onFilterAction());
        }

        // Configuration de la recherche
        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldText, newText) -> {
                // Implémenter la logique de recherche
                logger.debug("Recherche: {}", newText);
            });
        }

        setupMenuItems();

        // Configuration des boutons de la barre d'outils
        setupToolbarHandlers();

        // Configuration de la navigation clavier
        setupKeyboardNavigation();

        // Configuration des menus
        setupMenuHandlers();

        // Configuration des raccourcis globaux
        setupGlobalShortcuts();

        logger.debug("Gestionnaires d'événements configurés");
    }

    /**
     * Configuration des gestionnaires de menus - MÉTHODE CORRIGÉE
     */
    private void setupMenuHandlers() {
        logger.debug("Configuration des gestionnaires de menus");
        // Configuration des gestionnaires pour les menus
        if (menuFichier != null) {
            // Configurer les items du menu Fichier
        }
        if (menuAffaires != null) {
            // Configurer les items du menu Affaires
        }
    }

    /**
     * Configuration des raccourcis globaux - MÉTHODE CORRIGÉE
     */
    private void setupGlobalShortcuts() {
        logger.debug("Configuration des raccourcis globaux");

        Scene scene = getCurrentScene();
        if (scene != null) {
            // F1 - Aide
            scene.getAccelerators().put(
                    KeyCombination.keyCombination("F1"),
                    this::handleAideContextuelle
            );

            // F2 - Éditer en place
            scene.getAccelerators().put(
                    KeyCombination.keyCombination("F2"),
                    this::handleEditInPlace
            );

            // F3 - Recherche suivante
            scene.getAccelerators().put(
                    KeyCombination.keyCombination("F3"),
                    this::handleSearchNext
            );

            // F4 - Propriétés
            scene.getAccelerators().put(
                    KeyCombination.keyCombination("F4"),
                    this::handleShowProperties
            );

            // F5 - Actualiser
            scene.getAccelerators().put(
                    KeyCombination.keyCombination("F5"),
                    this::handleRefreshAction
            );

            // F6 - Basculer entre panneaux
            scene.getAccelerators().put(
                    KeyCombination.keyCombination("F6"),
                    this::handleSwitchPanel
            );

            // F7 - Vérifications
            scene.getAccelerators().put(
                    KeyCombination.keyCombination("F7"),
                    this::handleVerifications
            );

            // F8 - Historique
            scene.getAccelerators().put(
                    KeyCombination.keyCombination("F8"),
                    this::handleShowHistory
            );

            // F9 - Calculatrice
            scene.getAccelerators().put(
                    KeyCombination.keyCombination("F9"),
                    this::handleCalculator
            );

            // F10 - Menu principal
            scene.getAccelerators().put(
                    KeyCombination.keyCombination("F10"),
                    this::handleShowMainMenu
            );

            // F12 - Outils de développement
            scene.getAccelerators().put(
                    KeyCombination.keyCombination("F12"),
                    this::handleDeveloperTools
            );

            // Ctrl+S - Sauvegarder
            scene.getAccelerators().put(
                    KeyCombination.keyCombination("Ctrl+S"),
                    this::handleSaveAction
            );

            // Ctrl+F - Rechercher
            scene.getAccelerators().put(
                    KeyCombination.keyCombination("Ctrl+F"),
                    this::handleFocusSearch
            );

            // Escape - Annuler
            scene.getAccelerators().put(
                    KeyCombination.keyCombination("Escape"),
                    this::handleEscape
            );
        }
    }

    private Scene getCurrentScene() {
        if (currentStage != null) {
            return currentStage.getScene();
        }

        // Essayer de récupérer la scène depuis un composant FXML disponible
        if (contentPane != null) {
            return contentPane.getScene();
        }
        if (mainContentArea != null) {
            return mainContentArea.getScene();
        }
        if (menuBar != null) {
            return menuBar.getScene();
        }
        if (mainMenuBar != null) {
            return mainMenuBar.getScene();
        }

        return null;
    }

    /**
     * Configure les éléments de menu
     */
    private void setupMenuItems() {
        try {
            // Menu Fichier
            if (menuFichier != null) {
                MenuItem quitter = new MenuItem("Quitter");
                quitter.setOnAction(e -> Platform.exit());

                if (menuFichier.getItems().isEmpty()) {
                    menuFichier.getItems().add(quitter);
                }
            }

            // Menu Affaires
            if (menuAffaires != null) {
                MenuItem nouvelleAffaire = new MenuItem("Nouvelle Affaire...");
                nouvelleAffaire.setOnAction(e -> showNewAffaireDialog());

                MenuItem listeAffaires = new MenuItem("Liste des Affaires");
                listeAffaires.setOnAction(e -> loadView("/view/affaire-list.fxml"));

                if (menuAffaires.getItems().isEmpty()) {
                    menuAffaires.getItems().addAll(nouvelleAffaire, new SeparatorMenuItem(), listeAffaires);
                }
            }

            // Menu Contrevenants
            if (menuContrevenants != null) {
                MenuItem nouveauContrevenant = new MenuItem("Nouveau Contrevenant...");
                nouveauContrevenant.setOnAction(e -> showNewContrevenantDialog());

                MenuItem listeContrevenants = new MenuItem("Liste des Contrevenants");
                listeContrevenants.setOnAction(e -> loadView("/view/contrevenant-list.fxml"));

                if (menuContrevenants.getItems().isEmpty()) {
                    menuContrevenants.getItems().addAll(nouveauContrevenant, new SeparatorMenuItem(), listeContrevenants);
                }
            }

            // Configuration des boutons de la toolbar
            if (newAffaireButton != null) {
                newAffaireButton.setOnAction(e -> showNewAffaireDialog());
                newAffaireButton.setTooltip(new Tooltip("Nouvelle Affaire"));
                if (newAffaireButton.getGraphic() == null) {
                    newAffaireButton.setText("Nouvelle Affaire");
                } else {
                    newAffaireButton.setText("");
                }
            }

            // Menu Administration
            if (menuAdministration != null && authService.getCurrentUser().isAdmin()) {
                MenuItem gestionUtilisateurs = new MenuItem("Gestion des Utilisateurs");
                gestionUtilisateurs.setOnAction(e -> loadView("/view/user-management.fxml"));

                MenuItem referentiel = new MenuItem("Référentiels");
                referentiel.setOnAction(e -> loadView("/view/referentiel.fxml"));

                if (menuAdministration.getItems().isEmpty()) {
                    menuAdministration.getItems().addAll(gestionUtilisateurs, referentiel);
                }
            }

            // Menu Aide
            if (menuAide != null) {
                MenuItem aPropos = new MenuItem("À propos");
                aPropos.setOnAction(e -> showAboutDialog());

                menuAide.getItems().add(aPropos);
            }

            logger.info("Éléments de menu configurés avec succès");
        } catch (Exception e) {
            logger.error("Erreur lors de la configuration des éléments de menu", e);
        }
    }

    /**
     * Charge la vue par défaut
     */
    private void loadDefaultView() {
        try {
            // Afficher un message de bienvenue simple
            Label welcomeLabel = new Label("Bienvenue dans l'application de Gestion des Affaires Contentieuses");
            welcomeLabel.setStyle("-fx-font-size: 18px; -fx-padding: 20px;");

            if (authService.isAuthenticated()) {
                Label userWelcome = new Label("Connecté en tant que : " + authService.getCurrentUser().getDisplayName());
                userWelcome.setStyle("-fx-font-size: 14px; -fx-padding: 10px;");

                VBox welcomeBox = new VBox(10, welcomeLabel, userWelcome);
                welcomeBox.setStyle("-fx-alignment: center; -fx-padding: 50px;");

                // CORRIGÉ : utilise contentPane ou mainContentArea
                if (mainContentArea != null) {
                    mainContentArea.getChildren().clear();
                    mainContentArea.getChildren().add(welcomeBox);
                    logger.info("Vue par défaut chargée dans mainContentArea");
                } else if (contentPane != null) {
                    contentPane.setCenter(welcomeBox);
                    logger.info("Vue par défaut chargée dans contentPane");
                } else {
                    logger.warn("Aucun conteneur disponible pour charger la vue par défaut");
                }
            } else {
                if (mainContentArea != null) {
                    mainContentArea.getChildren().clear();
                    mainContentArea.getChildren().add(welcomeLabel);
                } else if (contentPane != null) {
                    contentPane.setCenter(welcomeLabel);
                } else {
                    logger.warn("Aucun conteneur disponible pour charger la vue par défaut");
                }
            }

            logger.info("Vue par défaut chargée");
        } catch (Exception e) {
            logger.error("Erreur lors du chargement de la vue par défaut", e);
        }
    }

    /**
     * Charge une vue dans le panneau central
     */
    private void loadView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node view = loader.load();

            // CORRIGÉ : utilise contentPane ou mainContentArea selon ce qui est disponible
            if (mainContentArea != null) {
                mainContentArea.getChildren().clear();
                mainContentArea.getChildren().add(view);
                logger.debug("Vue chargée dans mainContentArea: {}", fxmlPath);
            } else if (contentPane != null) {
                contentPane.setCenter(view);
                logger.debug("Vue chargée dans contentPane: {}", fxmlPath);
            } else {
                logger.warn("Impossible de charger la vue {} - aucun conteneur disponible", fxmlPath);
            }
        } catch (IOException e) {
            logger.error("Erreur lors du chargement de la vue: " + fxmlPath, e);

            // Afficher un message d'erreur dans le centre
            Label errorLabel = new Label("Erreur: Impossible de charger la vue " + fxmlPath);
            errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 14px; -fx-padding: 20px;");

            if (mainContentArea != null) {
                mainContentArea.getChildren().clear();
                mainContentArea.getChildren().add(errorLabel);
            } else if (contentPane != null) {
                contentPane.setCenter(errorLabel);
            }
        }
    }

    /**
     * Charge une vue avec titre (surcharge pour WelcomeController et autres)
     */
    public void loadView(String fxmlPath, String title) {
        loadView(fxmlPath);
        // Le titre pourrait être utilisé pour mettre à jour un label dans l'interface
        if (title != null && currentStage != null) {
            currentStage.setTitle(APP_TITLE + " - " + title);
        }
        logger.debug("Vue chargée avec titre: {} - {}", fxmlPath, title);
    }

    // ==================== GESTIONNAIRES D'ÉVÉNEMENTS ====================

    @FXML
    private void onLogout() {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirmation");
        confirmAlert.setHeaderText("Déconnexion");
        confirmAlert.setContentText("Voulez-vous vraiment vous déconnecter ?");

        if (confirmAlert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            authService.logout();
            stageManager.switchToLogin();
        }
    }

    @FXML
    private void onNewAction() {
        // Action générique de création - peut être spécialisée selon le contexte
        logger.debug("Action Nouveau déclenchée");
    }

    @FXML
    private void onEditAction() {
        logger.debug("Action Éditer déclenchée");
    }

    @FXML
    private void onDeleteAction() {
        logger.debug("Action Supprimer déclenchée");
    }

    @FXML
    private void onRefreshAction() {
        logger.debug("Action Actualiser déclenchée");
    }

    @FXML
    private void onPrintAction() {
        logger.debug("Action Imprimer déclenchée");
    }

    @FXML
    private void onFilterAction() {
        logger.debug("Action Filtrer déclenchée");
    }

    @FXML
    private void showNewAffaireDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/affaire-form.fxml"));
            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Nouvelle Affaire");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(contentPane.getScene().getWindow());

            Scene scene = new Scene(root);
            dialogStage.setScene(scene);
            dialogStage.showAndWait();

            // Rafraîchir la liste si nécessaire
            onRefreshAction();
        } catch (IOException e) {
            logger.error("Erreur lors de l'ouverture du formulaire de nouvelle affaire", e);
            showErrorDialog("Erreur", "Impossible d'ouvrir le formulaire", e.getMessage());
        }
    }

    @FXML
    private void showNewContrevenantDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/contrevenant-form.fxml"));
            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Nouveau Contrevenant");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(contentPane.getScene().getWindow());

            Scene scene = new Scene(root);
            dialogStage.setScene(scene);
            dialogStage.showAndWait();
        } catch (IOException e) {
            logger.error("Erreur lors de l'ouverture du formulaire de nouveau contrevenant", e);
            showErrorDialog("Erreur", "Impossible d'ouvrir le formulaire", e.getMessage());
        }
    }

    // ==================== MÉTHODES MANQUANTES CORRIGÉES ====================

    /**
     * Gestion de l'action Enter dans la recherche - CORRIGÉE
     */
    private void handleSearchEnterAction() {
        logger.debug("Recherche - action Enter");
        if (searchField != null && !searchField.getText().trim().isEmpty()) {
            performSearch(searchField.getText().trim());
        }
    }

    /**
     * Éditer en place (F2) - CORRIGÉE
     */
    private void handleEditInPlace() {
        logger.debug("F2 - Éditer en place");
        // Logique d'édition en place
    }

    /**
     * Recherche suivante (F3) - CORRIGÉE
     */
    private void handleSearchNext() {
        logger.debug("F3 - Recherche suivante");
        // Logique de recherche suivante
    }

    /**
     * Afficher propriétés (F4) - CORRIGÉE
     */
    private void handleShowProperties() {
        logger.debug("F4 - Propriétés");
        // Logique d'affichage des propriétés
    }

    /**
     * Basculer entre panneaux (F6) - CORRIGÉE
     */
    private void handleSwitchPanel() {
        logger.debug("F6 - Basculer entre panneaux");
        // Logique de basculement de panneaux
    }

    /**
     * Vérifications (F7) - CORRIGÉE
     */
    private void handleVerifications() {
        logger.debug("F7 - Vérifications");
        // Logique de vérifications
    }

    /**
     * Afficher historique (F8) - CORRIGÉE
     */
    private void handleShowHistory() {
        logger.debug("F8 - Historique");
        // Logique d'affichage de l'historique
    }

    /**
     * Calculatrice (F9) - CORRIGÉE
     */
    private void handleCalculator() {
        logger.debug("F9 - Calculatrice");
        // Logique de calculatrice
    }

    /**
     * Menu principal (F10) - CORRIGÉE
     */
    private void handleShowMainMenu() {
        logger.debug("F10 - Menu principal");
        // Logique d'affichage du menu principal
    }

    /**
     * Outils de développement (F12) - CORRIGÉE
     */
    private void handleDeveloperTools() {
        logger.debug("F12 - Outils de développement");
        // Logique des outils de développement
    }

    /**
     * Dialogue nouvel agent - CORRIGÉE
     */
    private void showNewAgentDialog() {
        logger.debug("Ouverture dialogue nouvel agent");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/agent-form.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Nouvel Agent");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            logger.error("Erreur ouverture dialogue agent", e);
        }
    }

    /**
     * Dialogue nouvel encaissement - CORRIGÉE
     */
    private void showNewEncaissementDialog() {
        logger.debug("Ouverture dialogue nouvel encaissement");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/encaissement-form.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Nouvel Encaissement");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            logger.error("Erreur ouverture dialogue encaissement", e);
        }
    }

    /**
     * Édition d'affaire - CORRIGÉE
     */
    private void editAffaire(Affaire affaire) {
        logger.debug("Édition affaire: {}", affaire.getNumeroAffaire());
        // Logique d'édition d'affaire
    }

    /**
     * Édition de contrevenant - CORRIGÉE
     */
    private void editContrevenant(Contrevenant contrevenant) {
        logger.debug("Édition contrevenant: {}", contrevenant.getCode());
        // Logique d'édition de contrevenant
    }

    /**
     * Édition d'agent - CORRIGÉE
     */
    private void editAgent(Agent agent) {
        logger.debug("Édition agent: {}", agent.getCodeAgent());
        // Logique d'édition d'agent
    }

    /**
     * Édition d'encaissement - CORRIGÉE
     */
    private void editEncaissement(Encaissement encaissement) {
        logger.debug("Édition encaissement: {}", encaissement.getReference());
        // Logique d'édition d'encaissement
    }

    /**
     * Suppression d'élément sélectionné - CORRIGÉE
     */
    private boolean deleteSelectedItem(String itemType, Object selectedItem) {
        logger.debug("Suppression élément: {} - {}", itemType, selectedItem);

        if (selectedItem == null) {
            AlertUtil.showWarningAlert("Aucun élément sélectionné",
                    "Veuillez sélectionner un élément à supprimer",
                    "Aucun " + itemType + " sélectionné");
            return false;
        }

        boolean confirm = AlertUtil.showConfirmation("Confirmation de suppression",
                "Êtes-vous sûr de vouloir supprimer cet élément ?",
                "Cette action est irréversible");

        if (confirm) {
            // Logique de suppression selon le type
            refreshCurrentView();
            return true;
        }
        return false;
    }

    /**
     * Actualisation de la vue courante - CORRIGÉE
     */
    private void refreshCurrentView() {
        logger.debug("Actualisation de la vue courante");
        // Logique d'actualisation
    }

    /**
     * Impression liste des affaires - CORRIGÉE
     */
    private void printAffairesList() {
        logger.debug("Impression liste des affaires");
        // Logique d'impression
    }

    /**
     * Impression rapport courant - CORRIGÉE
     */
    private void printCurrentReport() {
        logger.debug("Impression rapport courant");
        // Logique d'impression de rapport
    }

    /**
     * Affichage panneau de filtres - CORRIGÉE
     */
    private void showFilterPanel() {
        logger.debug("Affichage panneau de filtres");
        // Logique d'affichage des filtres
    }

    /**
     * Effacement de la recherche - CORRIGÉE
     */
    private void clearSearch() {
        logger.debug("Effacement de la recherche");
        if (searchField != null) {
            searchField.clear();
        }
    }

    /**
     * Exécution d'une recherche - CORRIGÉE
     */
    private void performSearch(String searchTerm) {
        logger.debug("Recherche: {}", searchTerm);
        // Logique de recherche
    }

    /**
     * Aide contextuelle - CORRIGÉE
     */
    private String getContextualHelp(String context) {
        logger.debug("Aide contextuelle: {}", context);
        return "Aide pour " + context;
    }

    /**
     * Affichage dialogue d'aide - CORRIGÉE
     */
    private void showHelpDialog(String helpContent) {
        logger.debug("Affichage aide: {}", helpContent);
        AlertUtil.showInfoAlert("Aide", "Aide contextuelle", helpContent);
    }

    /**
     * Action de sauvegarde - CORRIGÉE
     */
    private void handleSaveAction() {
        logger.debug("Ctrl+S - Sauvegarder");
        // Logique de sauvegarde
    }

    /**
     * Focus sur la recherche - CORRIGÉE
     */
    private void handleFocusSearch() {
        logger.debug("Ctrl+F - Focus recherche");
        if (searchField != null) {
            searchField.requestFocus();
        }
    }

    /**
     * Action d'échappement - CORRIGÉE
     */
    private void handleEscape() {
        logger.debug("Escape - Annuler");
        // Logique d'annulation
    }

    /**
     * Vérification des changements non sauvegardés - CORRIGÉE
     */
    private boolean hasUnsavedChanges() {
        logger.debug("Vérification changements non sauvegardés");
        return false; // Par défaut, aucun changement
    }

    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("À propos");
        alert.setHeaderText("Gestion des Affaires Contentieuses");
        alert.setContentText("Version 1.0.0\n\n" +
                "Application de gestion des affaires contentieuses\n" +
                "© 2024 - Tous droits réservés");
        alert.showAndWait();
    }

    private void setupToolbarHandlers() {
        logger.debug("Configuration des handlers ToolBar...");

        // Bouton Nouveau - Action contextuelle selon la vue active
        if (newButton != null) {
            newButton.setOnAction(e -> handleNewAction());
            newButton.setTooltip(new Tooltip("Nouveau (Ctrl+N)"));
            setupButtonIcon(newButton, "new-icon.png");
        }

        // Bouton Modifier - Action contextuelle
        if (editButton != null) {
            editButton.setOnAction(e -> handleEditAction());
            editButton.setTooltip(new Tooltip("Modifier (Ctrl+E)"));
            editButton.setDisable(true); // Activé quand sélection
            setupButtonIcon(editButton, "edit-icon.png");
        }

        // Bouton Supprimer - Action contextuelle
        if (deleteButton != null) {
            deleteButton.setOnAction(e -> handleDeleteAction());
            deleteButton.setTooltip(new Tooltip("Supprimer (Delete)"));
            deleteButton.setDisable(true); // Activé quand sélection
            setupButtonIcon(deleteButton, "delete-icon.png");
        }

        // Bouton Actualiser - Rafraîchir la vue courante
        if (refreshButton != null) {
            refreshButton.setOnAction(e -> handleRefreshAction());
            refreshButton.setTooltip(new Tooltip("Actualiser (F5)"));
            setupButtonIcon(refreshButton, "refresh-icon.png");
        }

        // Bouton Imprimer - Imprimer selon le contexte
        if (printButton != null) {
            printButton.setOnAction(e -> handlePrintAction());
            printButton.setTooltip(new Tooltip("Imprimer (Ctrl+P)"));
            setupButtonIcon(printButton, "print-icon.png");
        }

        // Bouton Filtrer - Ouvrir les options de filtrage
        if (filterButton != null) {
            filterButton.setOnAction(e -> handleFilterAction());
            filterButton.setTooltip(new Tooltip("Filtrer (Ctrl+F)"));
            setupButtonIcon(filterButton, "filter-icon.png");
        }

        // Champ de recherche - Recherche en temps réel
        if (searchField != null) {
            searchField.setPromptText("Rechercher...");
            searchField.textProperty().addListener((obs, oldVal, newVal) -> handleSearchAction(newVal));
            searchField.setOnAction(e -> handleSearchEnterAction());
        }

        logger.info("✅ Handlers ToolBar configurés");
    }

    /**
     * ENRICHISSEMENT : Navigation clavier F1-F12 selon le cahier des charges
     */
    private void setupKeyboardNavigation() {
        logger.debug("Configuration de la navigation clavier...");

        // Attendre que la scène soit disponible
        Platform.runLater(() -> {
            Scene scene = mainContentArea.getScene();
            if (scene != null) {
                // F1 - Aide contextuelle
                scene.getAccelerators().put(
                        KeyCombination.keyCombination("F1"),
                        () -> handleAideContextuelle()
                );

                // F2 - Renommer/Éditer en place
                scene.getAccelerators().put(
                        KeyCombination.keyCombination("F2"),
                        () -> handleEditInPlace()
                );

                // F3 - Recherche suivante
                scene.getAccelerators().put(
                        KeyCombination.keyCombination("F3"),
                        () -> handleSearchNext()
                );

                // F4 - Propriétés
                scene.getAccelerators().put(
                        KeyCombination.keyCombination("F4"),
                        () -> handleShowProperties()
                );

                // F5 - Actualiser
                scene.getAccelerators().put(
                        KeyCombination.keyCombination("F5"),
                        () -> handleRefreshAction()
                );

                // F6 - Basculer entre panneaux
                scene.getAccelerators().put(
                        KeyCombination.keyCombination("F6"),
                        () -> handleSwitchPanel()
                );

                // F7 - Vérifications
                scene.getAccelerators().put(
                        KeyCombination.keyCombination("F7"),
                        () -> handleVerifications()
                );

                // F8 - Historique
                scene.getAccelerators().put(
                        KeyCombination.keyCombination("F8"),
                        () -> handleShowHistory()
                );

                // F9 - Calculatrice
                scene.getAccelerators().put(
                        KeyCombination.keyCombination("F9"),
                        () -> handleCalculator()
                );

                // F10 - Menu principal
                scene.getAccelerators().put(
                        KeyCombination.keyCombination("F10"),
                        () -> handleShowMainMenu()
                );

                // F11 - Plein écran
                scene.getAccelerators().put(
                        KeyCombination.keyCombination("F11"),
                        () -> handleToggleFullscreen()
                );

                // F12 - Outils de développement
                scene.getAccelerators().put(
                        KeyCombination.keyCombination("F12"),
                        () -> handleDeveloperTools()
                );

                // Raccourcis Ctrl+
                setupControlShortcuts(scene);

                logger.info("✅ Navigation clavier F1-F12 configurée");
            }
        });
    }

    /**
     * ENRICHISSEMENT : Action Nouveau contextuelle selon la vue active
     */
    private void handleNewAction() {
        String currentView = getCurrentViewType();
        logger.debug("Action Nouveau pour la vue: {}", currentView);

        try {
            switch (currentView) {
                case "affaire-list":
                case "affaire":
                    showNewAffaireDialog();
                    break;
                case "contrevenant-list":
                case "contrevenant":
                    showNewContrevenantDialog();
                    break;
                case "agent-list":
                case "agent":
                    showNewAgentDialog();
                    break;
                case "encaissement-list":
                case "encaissement":
                    showNewEncaissementDialog();
                    break;
                case "mandat-list":
                case "mandat":
                    openMandatManagement();
                    break;
                default:
                    // Action par défaut : nouvelle affaire
                    showNewAffaireDialog();
                    break;
            }
        } catch (Exception e) {
            logger.error("Erreur lors de l'action Nouveau", e);
            AlertUtil.showErrorAlert("Erreur", "Impossible de créer un nouvel élément", e.getMessage());
        }
    }

    /**
     * ENRICHISSEMENT : Action Modifier contextuelle
     */
    private void handleEditAction() {
        String currentView = getCurrentViewType();
        logger.debug("Action Modifier pour la vue: {}", currentView);

        try {
            // Récupérer l'élément sélectionné dans la vue courante
            Object selectedItem = getSelectedItemFromCurrentView();

            if (selectedItem == null) {
                AlertUtil.showWarningAlert("Sélection requise",
                        "Aucun élément sélectionné",
                        "Veuillez sélectionner un élément à modifier.");
                return;
            }

            switch (currentView) {
                case "affaire-list":
                    if (selectedItem instanceof Affaire) {
                        editAffaire((Affaire) selectedItem);
                    }
                    break;
                case "contrevenant-list":
                    if (selectedItem instanceof Contrevenant) {
                        editContrevenant((Contrevenant) selectedItem);
                    }
                    break;
                case "agent-list":
                    if (selectedItem instanceof Agent) {
                        editAgent((Agent) selectedItem);
                    }
                    break;
                case "encaissement-list":
                    if (selectedItem instanceof Encaissement) {
                        editEncaissement((Encaissement) selectedItem);
                    }
                    break;
                default:
                    AlertUtil.showInfoAlert("Action non disponible",
                            "Modification",
                            "La modification n'est pas disponible pour cette vue.");
                    break;
            }
        } catch (Exception e) {
            logger.error("Erreur lors de l'action Modifier", e);
            AlertUtil.showErrorAlert("Erreur", "Impossible de modifier l'élément", e.getMessage());
        }
    }

    /**
     * ENRICHISSEMENT : Action Supprimer contextuelle avec confirmation
     */
    private void handleDeleteAction() {
        String currentView = getCurrentViewType();
        Object selectedItem = getSelectedItemFromCurrentView();

        if (selectedItem == null) {
            AlertUtil.showWarningAlert("Sélection requise",
                    "Aucun élément sélectionné",
                    "Veuillez sélectionner un élément à supprimer.");
            return;
        }

        // Vérification des droits
        if (!hasDeletePermission()) {
            AlertUtil.showWarningAlert("Accès refusé",
                    "Droits insuffisants",
                    "Vous n'avez pas les droits pour supprimer des éléments.");
            return;
        }

        // Confirmation de suppression
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation de suppression");
        confirm.setHeaderText("Supprimer définitivement ?");
        confirm.setContentText("Cette action est irréversible.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                boolean success = deleteSelectedItem(currentView, selectedItem);
                if (success) {
                    AlertUtil.showSuccess("Suppression", "Élément supprimé avec succès");
                    handleRefreshAction(); // Rafraîchir la vue
                }
            } catch (Exception e) {
                logger.error("Erreur lors de la suppression", e);
                AlertUtil.showErrorAlert("Erreur de suppression",
                        "Impossible de supprimer l'élément",
                        e.getMessage());
            }
        }
    }

    /**
     * ENRICHISSEMENT : Action Actualiser - Rafraîchit la vue courante
     */
    private void handleRefreshAction() {
        logger.debug("Action Actualiser");

        try {
            // Afficher un indicateur de chargement
            showLoadingIndicator(true);

            // Rafraîchir selon la vue courante
            refreshCurrentView();

            // Mettre à jour la barre de statut
            updateStatusLabel("Vue actualisée à " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));

        } catch (Exception e) {
            logger.error("Erreur lors de l'actualisation", e);
            AlertUtil.showErrorAlert("Erreur", "Impossible d'actualiser la vue", e.getMessage());
        } finally {
            showLoadingIndicator(false);
        }
    }

    /**
     * ENRICHISSEMENT : Action Imprimer contextuelle
     */
    private void handlePrintAction() {
        String currentView = getCurrentViewType();
        logger.debug("Action Imprimer pour la vue: {}", currentView);

        try {
            switch (currentView) {
                case "affaire-list":
                    printAffairesList();
                    break;
                case "rapport":
                    printCurrentReport();
                    break;
                default:
                    AlertUtil.showInfoAlert("Impression",
                            "Impression non disponible",
                            "L'impression n'est pas disponible pour cette vue.");
                    break;
            }
        } catch (Exception e) {
            logger.error("Erreur lors de l'impression", e);
            AlertUtil.showErrorAlert("Erreur d'impression",
                    "Impossible d'imprimer",
                    e.getMessage());
        }
    }

    /**
     * ENRICHISSEMENT : Action Filtrer - Ouvre le panneau de filtrage
     */
    private void handleFilterAction() {
        logger.debug("Action Filtrer");

        try {
            // Afficher le panneau de filtrage selon la vue
            showFilterPanel();
        } catch (Exception e) {
            logger.error("Erreur lors de l'ouverture du filtre", e);
            AlertUtil.showErrorAlert("Erreur", "Impossible d'ouvrir le filtre", e.getMessage());
        }
    }

    /**
     * ENRICHISSEMENT : Recherche en temps réel
     */
    private void handleSearchAction(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            clearSearch();
            return;
        }

        // Recherche avec délai pour éviter trop d'appels
        if (searchTask != null) {
            searchTask.stop();
        }

        searchTask = new Timeline(new KeyFrame(Duration.millis(300), e -> {
            performSearch(searchText.trim());
        }));
        searchTask.play();
    }

    // ==================== NAVIGATION CLAVIER F1-F12 ====================

    /**
     * ENRICHISSEMENT : F1 - Aide contextuelle
     */
    private void handleAideContextuelle() {
        String currentView = getCurrentViewType();
        logger.debug("F1 - Aide contextuelle pour: {}", currentView);

        try {
            String helpContent = getContextualHelp(currentView);
            showHelpDialog(helpContent);
        } catch (Exception e) {
            logger.error("Erreur lors de l'affichage de l'aide", e);
            AlertUtil.showInfoAlert("Aide", "Aide contextuelle",
                    "L'aide pour cette vue n'est pas encore disponible.");
        }
    }

    /**
     * ENRICHISSEMENT : F11 - Basculer plein écran
     */
    private void handleToggleFullscreen() {
        logger.debug("F11 - Basculer plein écran");

        try {
            Stage stage = getCurrentStage();
            if (stage != null) {
                stage.setFullScreen(!stage.isFullScreen());
                updateStatusLabel(stage.isFullScreen() ? "Mode plein écran activé" : "Mode fenêtré activé");
            }
        } catch (Exception e) {
            logger.error("Erreur lors du basculement plein écran", e);
        }
    }

    /**
     * ENRICHISSEMENT : Raccourcis Ctrl+ standards
     */
    private void setupControlShortcuts(Scene scene) {
        // Ctrl+N - Nouveau
        scene.getAccelerators().put(
                KeyCombination.keyCombination("Ctrl+N"),
                () -> handleNewAction()
        );

        // Ctrl+E - Éditer
        scene.getAccelerators().put(
                KeyCombination.keyCombination("Ctrl+E"),
                () -> handleEditAction()
        );

        // Ctrl+S - Sauvegarder
        scene.getAccelerators().put(
                KeyCombination.keyCombination("Ctrl+S"),
                () -> handleSaveAction()
        );

        // Ctrl+P - Imprimer
        scene.getAccelerators().put(
                KeyCombination.keyCombination("Ctrl+P"),
                () -> handlePrintAction()
        );

        // Ctrl+F - Rechercher
        scene.getAccelerators().put(
                KeyCombination.keyCombination("Ctrl+F"),
                () -> handleFocusSearch()
        );

        // Ctrl+Q - Quitter
        scene.getAccelerators().put(
                KeyCombination.keyCombination("Ctrl+Q"),
                () -> handleQuit()
        );

        // Delete - Supprimer
        scene.getAccelerators().put(
                KeyCombination.keyCombination("Delete"),
                () -> handleDeleteAction()
        );

        // Escape - Annuler/Fermer
        scene.getAccelerators().put(
                KeyCombination.keyCombination("Escape"),
                () -> handleEscape()
        );
    }

    /**
     * ENRICHISSEMENT : Déterminer le type de vue actuellement affichée
     */
    private String getCurrentViewType() {
        // Analyser le contenu de mainContentArea pour déterminer la vue
        if (mainContentArea.getChildren().isEmpty()) {
            return "welcome";
        }

        Node currentNode = mainContentArea.getChildren().get(0);

        // Analyser l'ID ou la classe pour déterminer le type
        if (currentNode.getId() != null) {
            String id = currentNode.getId();
            if (id.contains("affaire")) return "affaire-list";
            if (id.contains("contrevenant")) return "contrevenant-list";
            if (id.contains("agent")) return "agent-list";
            if (id.contains("encaissement")) return "encaissement-list";
            if (id.contains("rapport")) return "rapport";
            if (id.contains("mandat")) return "mandat-list";
        }

        return "unknown";
    }

    /**
     * ENRICHISSEMENT : Obtenir l'élément sélectionné dans la vue courante
     */
    private Object getSelectedItemFromCurrentView() {
        // Cette méthode doit être implémentée pour récupérer la sélection
        // depuis le contrôleur de la vue active

        String viewType = getCurrentViewType();

        // Utiliser une interface commune ou un pattern Observer
        // pour récupérer la sélection depuis les contrôleurs de vue

        return null; // À implémenter selon l'architecture des vues
    }

    /**
     * ENRICHISSEMENT : Vérifier les droits de suppression
     */
    private boolean hasDeletePermission() {
        if (!authService.isAuthenticated()) {
            return false;
        }

        RoleUtilisateur role = authService.getCurrentUser().getRole();
        return role == RoleUtilisateur.SUPER_ADMIN; // Seul SUPER_ADMIN peut supprimer
    }

    /**
     * ENRICHISSEMENT : Configuration des icônes des boutons
     */
    private void setupButtonIcon(Button button, String iconFileName) {
        try {
            InputStream iconStream = getClass().getResourceAsStream("/images/icons/" + iconFileName);
            if (iconStream != null) {
                ImageView icon = new ImageView(new Image(iconStream));
                icon.setFitWidth(16);
                icon.setFitHeight(16);
                button.setGraphic(icon);
            }
        } catch (Exception e) {
            logger.debug("Icône non trouvée: {}", iconFileName);
        }
    }

    /**
     * ENRICHISSEMENT : Affichage de l'indicateur de chargement
     */
    private void showLoadingIndicator(boolean show) {
        if (progressBar != null) {
            progressBar.setVisible(show);
            progressBar.setProgress(show ? -1 : 0); // Indeterminate progress
        }

        if (progressLabel != null) {
            progressLabel.setText(show ? "Chargement..." : "");
            progressLabel.setVisible(show);
        }

        // Changer le curseur
        mainContentArea.setCursor(show ? Cursor.WAIT : Cursor.DEFAULT);
    }

    /**
     * ENRICHISSEMENT : Mise à jour de la barre de statut
     */
    private void updateStatusLabel(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
        logger.debug("Statut: {}", message);
    }

    /**
     * ENRICHISSEMENT : Méthode pour quitter l'application
     */
    private void handleQuit() {
        // Vérifier s'il y a des modifications non sauvegardées
        if (hasUnsavedChanges()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Quitter l'application");
            confirm.setHeaderText("Des modifications non sauvegardées seront perdues");
            confirm.setContentText("Voulez-vous vraiment quitter ?");

            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }

        // Fermer l'application proprement
        Platform.exit();
    }

    // ==================== MÉTHODES PUBLIQUES ====================

    /**
     * Met à jour le label de statut
     */
    public void updateStatus(String status) {
        if (statusLabel != null) {
            Platform.runLater(() -> statusLabel.setText(status));
        }
    }

    /**
     * Met à jour la barre de progression
     */
    public void updateProgress(double progress, String message) {
        Platform.runLater(() -> {
            if (progressBar != null) {
                progressBar.setProgress(progress);
            }
            if (progressLabel != null) {
                progressLabel.setText(message);
            }
        });
    }

    /**
     * Active/Désactive les boutons de la toolbar
     */
    public void setToolbarButtonsDisable(boolean disable) {
        if (newButton != null) newButton.setDisable(disable);
        if (editButton != null) editButton.setDisable(disable);
        if (deleteButton != null) deleteButton.setDisable(disable);
        if (refreshButton != null) refreshButton.setDisable(disable);
        if (printButton != null) printButton.setDisable(disable);
    }

    /**
     * Met à jour l'heure dans la barre de statut
     */
    private void updateDateTime() {
        String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        // Implémenter si un label de date/heure existe
    }

    /**
     * Change le curseur de l'application
     */
    public void setCursor(Cursor cursor) {
        if (contentPane != null && contentPane.getScene() != null) {
            contentPane.getScene().setCursor(cursor);
        }
    }

    /**
     * Retourne le mandat actif ou null
     */
    public Mandat getMandatActif() {
        return MandatService.getInstance().getMandatActif();
    }

    /**
     * Vérifie si un mandat est actif
     */
    public boolean hasMandatActif() {
        return MandatService.getInstance().hasMandatActif();
    }

    /**
     * Ouvre la fenêtre de gestion des mandats
     */
    private void openMandatManagement() {
        logger.info("Ouverture de la gestion des mandats");

        try {
            // Charger la vue
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/mandat-management.fxml"));
            Parent root = loader.load();

            // Créer une nouvelle fenêtre modale
            Stage mandatStage = new Stage();
            mandatStage.setTitle("Gestion des Mandats");
            mandatStage.initModality(Modality.APPLICATION_MODAL);

            // Récupérer le stage parent de manière sécurisée
            Scene currentScene = getCurrentScene();
            if (currentScene != null && currentScene.getWindow() instanceof Stage) {
                mandatStage.initOwner(currentScene.getWindow());
            }

            // Créer la scène
            Scene scene = new Scene(root);

            // Appliquer les styles
            scene.getStylesheets().add(getClass().getResource("/css/main-styles.css").toExternalForm());
            if (getClass().getResource("/css/mandat-styles.css") != null) {
                scene.getStylesheets().add(getClass().getResource("/css/mandat-styles.css").toExternalForm());
            }

            // Configurer la fenêtre
            mandatStage.setScene(scene);
            mandatStage.setMinWidth(1000);
            mandatStage.setMinHeight(700);
            mandatStage.centerOnScreen();

            // Afficher et attendre la fermeture
            mandatStage.showAndWait();

            // Rafraîchir l'affichage après fermeture
            updateMandatActif();

            // Notifier les autres composants si nécessaire
            if (welcomeController != null) {
                welcomeController.refreshStatistics();
            }

        } catch (IOException e) {
            logger.error("Erreur lors de l'ouverture de la gestion des mandats", e);
            showErrorDialog("Erreur", "Impossible d'ouvrir la gestion des mandats", e.getMessage());
        }
    }

    /**
     * Met à jour l'affichage du mandat actif dans la barre de statut
     */
    private void updateMandatActif() {
        Platform.runLater(() -> {
            if (mandatLabel != null) {
                Mandat mandatActif = MandatService.getInstance().getMandatActif();
                if (mandatActif != null) {
                    String displayText = mandatActif.getNumeroMandat();
                    if (mandatActif.getDescription() != null && !mandatActif.getDescription().isEmpty()) {
                        displayText += " - " + mandatActif.getDescription();
                    }
                    mandatLabel.setText("Mandat actif: " + displayText);
                    mandatLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                } else {
                    mandatLabel.setText("Aucun mandat actif");
                    mandatLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                }
            }
        });
    }

    /**
     * Affiche un dialogue d'erreur
     */
    private void showErrorDialog(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}