package com.regulation.contentieux;

import atlantafx.base.theme.PrimerLight;
import com.regulation.contentieux.config.DatabaseConfig;
import com.regulation.contentieux.util.FXMLLoaderUtil;
import com.regulation.contentieux.util.StageManager;
import com.regulation.contentieux.util.AlertUtil;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import javafx.scene.Parent;
import javafx.scene.image.Image;
import java.util.Objects;

/**
 * Classe principale de l'application de gestion des affaires contentieuses
 * VERSION OPTIMISÉE - Sans vérifications au démarrage
 */
public class Main extends Application {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    // Configuration de l'application
    private static final String APP_TITLE = "Gestion des Affaires Contentieuses";
    private static final String APP_VERSION = "1.0.0";
    private static final String APP_ICON = "/images/app-icon.png";

    // Dimensions de la fenêtre de connexion
    private static final int LOGIN_WIDTH = 650;
    private static final int LOGIN_HEIGHT = 550;

    @Override
    public void init() throws Exception {
        super.init();

        logger.info("=== DÉMARRAGE DE L'APPLICATION ===");
        logger.info("Application: {} v{}", APP_TITLE, APP_VERSION);

        // Initialisation minimale de la base de données
        try {
            initializeDatabase();
            logger.info("Base de données initialisée");
        } catch (Exception e) {
            logger.error("Erreur lors de l'initialisation de la base de données", e);
            throw new RuntimeException("Impossible d'initialiser la base de données", e);
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        logger.info("🚀 Démarrage de l'application");

        try {
            // 1. Configuration du thème
            setupTheme();

            // 2. Configuration du gestionnaire de scènes
            StageManager.getInstance().setPrimaryStage(primaryStage);

            // 3. Configuration de la fenêtre principale
            setupPrimaryStage(primaryStage);

            // 4. Chargement direct de l'écran de connexion
            loadLoginScreen(primaryStage);

            // 5. Affichage de la fenêtre
            primaryStage.show();

            logger.info("✅ Application démarrée");

        } catch (Exception e) {
            logger.error("❌ Erreur critique lors du démarrage", e);
            showStartupError(e);
            Platform.exit();
        }
    }

    @Override
    public void stop() throws Exception {
        logger.info("=== ARRÊT DE L'APPLICATION ===");
        super.stop();
        logger.info("Application fermée");
    }

    /**
     * Configuration du thème AtlantaFX
     */
    private void setupTheme() {
        try {
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            logger.debug("Thème AtlantaFX appliqué");
        } catch (Exception e) {
            logger.warn("Thème par défaut utilisé");
        }
    }

    /**
     * Configuration de la fenêtre principale
     */
    private void setupPrimaryStage(Stage primaryStage) {
        primaryStage.setTitle(APP_TITLE + " - v" + APP_VERSION);

        // Icône de l'application (optionnelle)
        try {
            Image icon = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream(APP_ICON)));
            primaryStage.getIcons().add(icon);
        } catch (Exception e) {
            // Pas d'icône, ce n'est pas grave
        }

        primaryStage.setOnCloseRequest(event -> {
            logger.info("Fermeture de l'application");
            Platform.exit();
        });

        primaryStage.setMinWidth(400);
        primaryStage.setMinHeight(300);
    }

    /**
     * Chargement direct de l'écran de connexion
     */
    private void loadLoginScreen(Stage primaryStage) throws Exception {
        try {
            Parent loginView = FXMLLoaderUtil.loadParent("view/login.fxml");
            Scene loginScene = new Scene(loginView, LOGIN_WIDTH, LOGIN_HEIGHT);
            applyStylesheets(loginScene);

            primaryStage.setScene(loginScene);
            primaryStage.setResizable(false);
            primaryStage.setWidth(LOGIN_WIDTH);
            primaryStage.setHeight(LOGIN_HEIGHT);
            primaryStage.centerOnScreen();

            logger.debug("Écran de connexion chargé");
        } catch (Exception e) {
            logger.error("Impossible de charger l'écran de connexion", e);
            throw e;
        }
    }

    /**
     * Application des feuilles de style
     */
    private void applyStylesheets(Scene scene) {
        try {
            String stylesheet = Objects.requireNonNull(
                    getClass().getResource("/css/application.css")).toExternalForm();
            scene.getStylesheets().add(stylesheet);
        } catch (Exception e) {
            // Pas de CSS personnalisé, utiliser le thème par défaut
        }
    }

    /**
     * Initialisation minimale de la base de données
     */
    private void initializeDatabase() throws Exception {
        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            if (conn == null) {
                throw new RuntimeException("Impossible d'établir la connexion SQLite");
            }
        }
    }

    /**
     * Affichage d'une erreur de démarrage
     */
    private void showStartupError(Exception e) {
        try {
            AlertUtil.showErrorAlert("Erreur de démarrage",
                    "Impossible de démarrer l'application",
                    "Détails: " + e.getMessage());
        } catch (Exception alertException) {
            System.err.println("ERREUR CRITIQUE: " + e.getMessage());
        }
    }

    /**
     * Point d'entrée de l'application
     */
    public static void main(String[] args) {
        try {
            setupSystemProperties();
            launch(args);
        } catch (Exception e) {
            System.err.println("ERREUR FATALE: Impossible de démarrer l'application");
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Configuration des propriétés système
     */
    private static void setupSystemProperties() {
        if (System.getProperty("logback.configurationFile") == null) {
            System.setProperty("logback.configurationFile", "logback.xml");
        }

        System.setProperty("javafx.preloader", "false");

        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            System.setProperty("glass.accessible.force", "false");
        }

        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.subpixeltext", "false");
    }
}