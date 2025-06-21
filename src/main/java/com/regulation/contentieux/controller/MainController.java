package com.regulation.contentieux.controller;

import javafx.scene.layout.StackPane;
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

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

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

    // Labels et boutons présents dans le FXML
    @FXML private Label titleLabel;
    @FXML private Label userInfoLabel;
    @FXML private Label statusLabel;           // dans la barre de statut
    @FXML private Label connectionStatusLabel; // dans la barre de statut
    @FXML private Label progressLabel;         // dans la barre de statut
    @FXML private ProgressBar progressBar;     // dans la barre de statut

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
    }

    /**
     * Configure les informations utilisateur
     */
    private void setupUserInfo() {
        if (authService.isAuthenticated() && userInfoLabel != null) {
            String userInfo = authService.getCurrentUser().getDisplayName() +
                    " (" + authService.getCurrentUser().getRole().getLibelle() + ")";
            userInfoLabel.setText(userInfo);
            logger.debug("Informations utilisateur configurées: {}", userInfo);
        } else if (userInfoLabel != null) {
            userInfoLabel.setText("Non connecté");
        }
    }

    /**
     * Configure l'accès aux menus selon les permissions
     * CORRIGÉ : utilise les Menu au lieu des TitledPane
     */
    private void setupMenuAccess() {
        if (!authService.isAuthenticated()) {
            logger.warn("Aucun utilisateur connecté pour configurer les permissions");
            return;
        }

        try {
            // Gestion des affaires
            if (menuAffaires != null) {
                boolean canManageAffaires = authService.hasPermission(RoleUtilisateur.Permission.GESTION_AFFAIRES);
                menuAffaires.setDisable(!canManageAffaires);
                logger.debug("Menu Affaires - Accès: {}", canManageAffaires);
            }

            // Gestion des agents
            if (menuAgents != null) {
                boolean canManageAgents = authService.hasPermission(RoleUtilisateur.Permission.GESTION_AGENTS);
                menuAgents.setDisable(!canManageAgents);
                logger.debug("Menu Agents - Accès: {}", canManageAgents);
            }

            // Gestion des contrevenants
            if (menuContrevenants != null) {
                boolean canManageContrevenants = authService.hasPermission(RoleUtilisateur.Permission.GESTION_CONTREVENANTS);
                menuContrevenants.setDisable(!canManageContrevenants);
                logger.debug("Menu Contrevenants - Accès: {}", canManageContrevenants);
            }

            // Gestion des encaissements
            if (menuEncaissements != null) {
                boolean canManageEncaissements = authService.hasPermission(RoleUtilisateur.Permission.GESTION_ENCAISSEMENTS);
                menuEncaissements.setDisable(!canManageEncaissements);
                logger.debug("Menu Encaissements - Accès: {}", canManageEncaissements);
            }

            // Génération des rapports
            if (menuRapports != null) {
                boolean canGenerateRapports = authService.hasPermission(RoleUtilisateur.Permission.GENERATION_RAPPORTS);
                menuRapports.setDisable(!canGenerateRapports);
                logger.debug("Menu Rapports - Accès: {}", canGenerateRapports);
            }

            // Administration
            if (menuAdministration != null) {
                boolean canManageUsers = authService.hasPermission(RoleUtilisateur.Permission.GESTION_UTILISATEURS);
                boolean canManageReferentiel = authService.hasPermission(RoleUtilisateur.Permission.GESTION_REFERENTIEL);
                menuAdministration.setDisable(!canManageUsers && !canManageReferentiel);
                logger.debug("Menu Administration - Accès: {}", canManageUsers || canManageReferentiel);
            }

        } catch (Exception e) {
            logger.error("Erreur lors de la configuration des permissions de menu", e);
        }
    }

    /**
     * Configure les gestionnaires d'événements
     */
    private void setupEventHandlers() {
        // Bouton de déconnexion
        if (logoutButton != null) {
            logoutButton.setOnAction(e -> onLogout());
        }

        // Boutons de la toolbar
        if (newButton != null) {
            newButton.setOnAction(e -> onNew());
        }

        if (editButton != null) {
            editButton.setOnAction(e -> onEdit());
        }

        if (deleteButton != null) {
            deleteButton.setOnAction(e -> onDelete());
        }

        if (refreshButton != null) {
            refreshButton.setOnAction(e -> onRefresh());
        }

        if (printButton != null) {
            printButton.setOnAction(e -> onPrint());
        }

        if (filterButton != null) {
            filterButton.setOnAction(e -> onFilter());
        }

        // Champ de recherche
        if (searchField != null) {
            searchField.setOnAction(e -> onSearch());
        }

        // AJOUT : Configuration des menus avec des MenuItems
        setupMenuItems();
    }

    /**
     * Configure les éléments de menu avec des actions
     */
    private void setupMenuItems() {
        try {
            // Menu Affaires
            if (menuAffaires != null) {
                MenuItem nouvelleAffaire = new MenuItem("Nouvelle Affaire");
                nouvelleAffaire.setOnAction(e -> onMenuAffaires());

                MenuItem listeAffaires = new MenuItem("Liste des Affaires");
                listeAffaires.setOnAction(e -> loadView("/view/affaire-list.fxml"));

                menuAffaires.getItems().addAll(nouvelleAffaire, listeAffaires);
            }

            // Menu Contrevenants
            if (menuContrevenants != null) {
                MenuItem nouveauContrevenant = new MenuItem("Nouveau Contrevenant");
                nouveauContrevenant.setOnAction(e -> onMenuContrevenants());

                MenuItem listeContrevenants = new MenuItem("Liste des Contrevenants");
                listeContrevenants.setOnAction(e -> loadView("/view/contrevenant-list.fxml"));

                menuContrevenants.getItems().addAll(nouveauContrevenant, listeContrevenants);
            }

            // Menu Agents
            if (menuAgents != null) {
                MenuItem nouveauAgent = new MenuItem("Nouveau Agent");
                nouveauAgent.setOnAction(e -> onMenuAgents());

                MenuItem listeAgents = new MenuItem("Liste des Agents");
                listeAgents.setOnAction(e -> loadView("/view/agent-list.fxml"));

                menuAgents.getItems().addAll(nouveauAgent, listeAgents);
            }

            // Menu Encaissements
            if (menuEncaissements != null) {
                MenuItem nouvelEncaissement = new MenuItem("Nouvel Encaissement");
                nouvelEncaissement.setOnAction(e -> onMenuEncaissements());

                MenuItem listeEncaissements = new MenuItem("Liste des Encaissements");
                listeEncaissements.setOnAction(e -> loadView("/view/encaissement-list.fxml"));

                menuEncaissements.getItems().addAll(nouvelEncaissement, listeEncaissements);
            }

            // Menu Rapports
            if (menuRapports != null) {
                MenuItem rapportRepartition = new MenuItem("Rapport de Répartition");
                rapportRepartition.setOnAction(e -> loadView("/view/rapport-repartition.fxml"));

                MenuItem tableauAmendes = new MenuItem("Tableau des Amendes");
                tableauAmendes.setOnAction(e -> onMenuRapports());

                menuRapports.getItems().addAll(rapportRepartition, tableauAmendes);
            }

            // Menu Administration
            if (menuAdministration != null) {
                MenuItem gestionUtilisateurs = new MenuItem("Gestion des Utilisateurs");
                gestionUtilisateurs.setOnAction(e -> loadView("/view/user-management.fxml"));

                MenuItem referentiel = new MenuItem("Référentiel");
                referentiel.setOnAction(e -> onMenuAdministration());

                menuAdministration.getItems().addAll(gestionUtilisateurs, referentiel);
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

        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                authService.logout();
                try {
                    // CORRIGÉ : utilise switchToLogin() au lieu de showLoginStage()
                    stageManager.switchToLogin();
                    logger.info("Déconnexion réussie, retour à l'écran de connexion");
                } catch (Exception e) {
                    logger.error("Erreur lors de la déconnexion", e);
                    // Fallback : fermer l'application
                    Platform.exit();
                }
            }
        });
    }

    @FXML
    private void onNew() {
        logger.info("Action: Nouveau");
        // TODO: Implémenter l'action nouveau
    }

    @FXML
    private void onEdit() {
        logger.info("Action: Éditer");
        // TODO: Implémenter l'action éditer
    }

    @FXML
    private void onDelete() {
        logger.info("Action: Supprimer");
        // TODO: Implémenter l'action supprimer
    }

    @FXML
    private void onRefresh() {
        logger.info("Action: Actualiser");
        // TODO: Implémenter l'action actualiser
    }

    @FXML
    private void onPrint() {
        logger.info("Action: Imprimer");
        // TODO: Implémenter l'action imprimer
    }

    @FXML
    private void onFilter() {
        logger.info("Action: Filtrer");
        // TODO: Implémenter l'action filtrer
    }

    @FXML
    private void onSearch() {
        String searchText = searchField.getText();
        logger.info("Action: Rechercher '{}'", searchText);
        // TODO: Implémenter la recherche
    }

    // Méthodes pour les menus (maintenant fonctionnelles)
    public void onMenuAffaires() {
        logger.info("Menu: Affaires");
        loadView("/view/affaire-list.fxml");
    }

    public void onMenuContrevenants() {
        logger.info("Menu: Contrevenants");
        loadView("/view/contrevenant-list.fxml");
    }

    public void onMenuAgents() {
        logger.info("Menu: Agents");
        loadView("/view/agent-list.fxml");
    }

    public void onMenuEncaissements() {
        logger.info("Menu: Encaissements");
        loadView("/view/encaissement-list.fxml");
    }

    public void onMenuRapports() {
        logger.info("Menu: Rapports");
        loadView("/view/rapport-list.fxml");
    }

    public void onMenuAdministration() {
        logger.info("Menu: Administration");
        loadView("/view/user-management.fxml");
    }

    /**
     * Affiche la boîte de dialogue À propos
     */
    private void showAboutDialog() {
        Alert aboutAlert = new Alert(Alert.AlertType.INFORMATION);
        aboutAlert.setTitle("À propos");
        aboutAlert.setHeaderText("Gestion des Affaires Contentieuses");
        aboutAlert.setContentText("Version 1.0.0\n\nApplication de gestion des affaires contentieuses\nDéveloppée avec JavaFX");
        aboutAlert.showAndWait();
    }
}