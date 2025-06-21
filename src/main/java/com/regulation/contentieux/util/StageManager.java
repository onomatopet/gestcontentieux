package com.regulation.contentieux.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Gestionnaire centralisé des fenêtres de l'application
 * Pattern Singleton pour garantir une instance unique
 */
public class StageManager {

    private static final Logger logger = LoggerFactory.getLogger(StageManager.class);
    private static StageManager instance;

    private Stage primaryStage;
    private final Map<String, Stage> openStages = new HashMap<>();

    // Chemins des vues FXML
    private static final String LOGIN_VIEW = "/fxml/login.fxml";
    private static final String MAIN_VIEW = "/fxml/main.fxml";
    private static final String CHANGE_PASSWORD_VIEW = "/fxml/change-password.fxml";

    // Configuration de l'application
    private static final String APP_TITLE = "Gestion des Affaires Contentieuses";
    private static final String APP_ICON = "/images/icon.png";
    private static final int MIN_WIDTH = 1024;
    private static final int MIN_HEIGHT = 768;

    private StageManager() {
        // Constructeur privé pour le singleton
    }

    /**
     * Obtient l'instance unique du StageManager
     */
    public static synchronized StageManager getInstance() {
        if (instance == null) {
            instance = new StageManager();
        }
        return instance;
    }

    /**
     * Initialise le stage principal
     */
    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
        configurePrimaryStage();
    }

    /**
     * Configure le stage principal
     */
    private void configurePrimaryStage() {
        if (primaryStage != null) {
            primaryStage.setTitle(APP_TITLE);
            primaryStage.setMinWidth(MIN_WIDTH);
            primaryStage.setMinHeight(MIN_HEIGHT);

            // Icône de l'application
            try {
                Image icon = new Image(Objects.requireNonNull(
                        getClass().getResourceAsStream(APP_ICON)));
                primaryStage.getIcons().add(icon);
            } catch (Exception e) {
                logger.warn("Impossible de charger l'icône de l'application", e);
            }

            // Gestion de la fermeture
            primaryStage.setOnCloseRequest(event -> {
                closeAllStages();
            });
        }
    }

    /**
     * Affiche la vue de connexion
     */
    public void showLoginView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(LOGIN_VIEW));
            Parent root = loader.load();

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());

            primaryStage.setScene(scene);
            primaryStage.setResizable(false);
            primaryStage.centerOnScreen();
            primaryStage.show();

            logger.info("Vue de connexion affichée");

        } catch (IOException e) {
            logger.error("Erreur lors du chargement de la vue de connexion", e);
            showErrorDialog("Erreur", "Impossible de charger la vue de connexion");
        }
    }

    /**
     * Affiche la vue principale
     */
    public void showMainView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(MAIN_VIEW));
            Parent root = loader.load();

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());

            primaryStage.setScene(scene);
            primaryStage.setResizable(true);
            primaryStage.setMaximized(true);
            primaryStage.show();

            logger.info("Vue principale affichée");

        } catch (IOException e) {
            logger.error("Erreur lors du chargement de la vue principale", e);
            showErrorDialog("Erreur", "Impossible de charger la vue principale");
        }
    }

    /**
     * Bascule vers la vue de connexion
     */
    public void switchToLogin() {
        closeAllStages();
        showLoginView();
    }

    /**
     * Bascule vers la vue principale
     */
    public void switchToMain() {
        showMainView();
    }

    /**
     * Affiche une fenêtre modale
     */
    public Stage showModalWindow(String fxmlPath, String title, Stage owner) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            Stage modalStage = new Stage();
            modalStage.setTitle(title);
            modalStage.initModality(Modality.WINDOW_MODAL);
            modalStage.initOwner(owner != null ? owner : primaryStage);

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());

            modalStage.setScene(scene);
            modalStage.setResizable(false);
            modalStage.centerOnScreen();

            // Stocker la référence
            openStages.put(fxmlPath, modalStage);

            // Retirer de la map à la fermeture
            modalStage.setOnHidden(e -> openStages.remove(fxmlPath));

            modalStage.show();

            logger.debug("Fenêtre modale affichée: {}", title);

            return modalStage;

        } catch (IOException e) {
            logger.error("Erreur lors de l'ouverture de la fenêtre modale: " + fxmlPath, e);
            showErrorDialog("Erreur", "Impossible d'ouvrir la fenêtre");
            return null;
        }
    }

    /**
     * Affiche le dialogue de changement de mot de passe
     */
    public void showChangePasswordDialog() {
        showModalWindow(CHANGE_PASSWORD_VIEW, "Changer le mot de passe", primaryStage);
    }

    /**
     * Crée et affiche une nouvelle fenêtre
     */
    public Stage createStage(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle(title);

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());

            stage.setScene(scene);

            // Icône
            try {
                Image icon = new Image(Objects.requireNonNull(
                        getClass().getResourceAsStream(APP_ICON)));
                stage.getIcons().add(icon);
            } catch (Exception e) {
                logger.debug("Icône non trouvée pour la fenêtre: {}", title);
            }

            // Stocker la référence
            openStages.put(fxmlPath, stage);

            // Retirer de la map à la fermeture
            stage.setOnHidden(e -> openStages.remove(fxmlPath));

            logger.debug("Nouvelle fenêtre créée: {}", title);

            return stage;

        } catch (IOException e) {
            logger.error("Erreur lors de la création de la fenêtre: " + fxmlPath, e);
            showErrorDialog("Erreur", "Impossible de créer la fenêtre");
            return null;
        }
    }

    /**
     * Charge une vue dans un conteneur existant
     */
    public Object loadView(String fxmlPath) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        loader.load();
        return loader.getController();
    }

    /**
     * Obtient le contrôleur d'une vue
     */
    public <T> T getController(String fxmlPath) {
        Stage stage = openStages.get(fxmlPath);
        if (stage != null && stage.getUserData() instanceof FXMLLoader) {
            FXMLLoader loader = (FXMLLoader) stage.getUserData();
            return loader.getController();
        }
        return null;
    }

    /**
     * Ferme une fenêtre spécifique
     */
    public void closeStage(String fxmlPath) {
        Stage stage = openStages.get(fxmlPath);
        if (stage != null) {
            stage.close();
            openStages.remove(fxmlPath);
        }
    }

    /**
     * Ferme toutes les fenêtres secondaires
     */
    public void closeAllStages() {
        openStages.values().forEach(Stage::close);
        openStages.clear();
    }

    /**
     * Affiche un dialogue d'erreur
     */
    private void showErrorDialog(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);

        Stage alertStage = (Stage) alert.getDialogPane().getScene().getWindow();
        try {
            Image icon = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream(APP_ICON)));
            alertStage.getIcons().add(icon);
        } catch (Exception e) {
            logger.debug("Icône non trouvée pour l'alerte");
        }

        alert.showAndWait();
    }

    /**
     * Obtient le stage principal
     */
    public Stage getPrimaryStage() {
        return primaryStage;
    }

    /**
     * Vérifie si une fenêtre est ouverte
     */
    public boolean isStageOpen(String fxmlPath) {
        return openStages.containsKey(fxmlPath) &&
                openStages.get(fxmlPath).isShowing();
    }

    /**
     * Centre une fenêtre sur l'écran
     */
    public void centerStage(Stage stage) {
        stage.centerOnScreen();
    }

    /**
     * Définit les dimensions d'une fenêtre
     */
    public void setStageSize(Stage stage, double width, double height) {
        stage.setWidth(width);
        stage.setHeight(height);
        stage.centerOnScreen();
    }
}