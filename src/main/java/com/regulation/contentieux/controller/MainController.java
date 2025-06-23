package com.regulation.contentieux.controller;

import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.regulation.contentieux.model.Mandat;
import com.regulation.contentieux.service.MandatService;
import javafx.stage.Modality;

import com.regulation.contentieux.model.enums.RoleUtilisateur;
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

import java.io.IOException;
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
        Scene scene = mainMenuBar.getScene();
        if (scene != null) {
            scene.getAccelerators().put(
                    KeyCombination.keyCombination("Ctrl+M"),
                    () -> openMandatManagement()
            );
        }

        // Mettre à jour l'affichage du mandat actif
        updateMandatActif();

        // Configurer la mise à jour périodique du mandat actif (optionnel)
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(30), e -> updateMandatActif()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void setupWindowFocusListener() {
        // Quand la fenêtre principale redevient active, rafraîchir le mandat
        Stage primaryStage = (Stage) mainMenuBar.getScene().getWindow();
        primaryStage.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused && !wasFocused) {
                // La fenêtre vient de reprendre le focus
                updateMandatActif();
            }
        });
    }

    private void addMandatButtonToToolbar() {
        if (mainToolBar != null) {
            // Créer un bouton pour la gestion des mandats
            Button mandatButton = new Button();
            mandatButton.setTooltip(new Tooltip("Gestion des Mandats (Ctrl+M)"));

            // Ajouter une icône
            ImageView icon = new ImageView(new Image(getClass().getResourceAsStream("/images/mandat-icon.png")));
            icon.setFitWidth(16);
            icon.setFitHeight(16);
            mandatButton.setGraphic(icon);

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

    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("À propos");
        alert.setHeaderText("Gestion des Affaires Contentieuses");
        alert.setContentText("Version 1.0.0\n\n" +
                "Application de gestion des affaires contentieuses\n" +
                "© 2024 - Tous droits réservés");
        alert.showAndWait();
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
            mandatStage.initOwner(mainMenuBar.getScene().getWindow());

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