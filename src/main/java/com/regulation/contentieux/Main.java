package com.regulation.contentieux;

import atlantafx.base.theme.PrimerLight;
import com.regulation.contentieux.config.DatabaseConfig;
import com.regulation.contentieux.util.FXMLLoaderUtil;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Classe principale de l'application de gestion des affaires contentieuses
 */
public class Main extends Application {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    // Configuration de l'application
    private static final String APP_TITLE = "Gestion des Affaires Contentieuses";
    private static final String APP_VERSION = "1.0.0";
    private static final String APP_ICON = "/images/app-icon.png";

    // Dimensions de la fenêtre de connexion
    private static final int LOGIN_WIDTH = 400;
    private static final int LOGIN_HEIGHT = 350;

    @Override
    public void init() throws Exception {
        super.init();

        logger.info("=== DÉMARRAGE DE L'APPLICATION ===");
        logger.info("Application: {} v{}", APP_TITLE, APP_VERSION);
        logger.info("Java Version: {}", System.getProperty("java.version"));
        logger.info("JavaFX Version: {}", System.getProperty("javafx.version"));
        logger.info("OS: {} {}", System.getProperty("os.name"), System.getProperty("os.version"));

        // Initialisation des composants critiques
        try {
            initializeDatabase();
            logger.info("Initialisation de la base de données: OK");
        } catch (Exception e) {
            logger.error("Erreur lors de l'initialisation de la base de données", e);
            throw new RuntimeException("Impossible d'initialiser la base de données", e);
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        try {
            // Configuration du thème AtlantaFX
            setupTheme();

            // Configuration de la fenêtre principale
            setupPrimaryStage(primaryStage);

            // Chargement de l'écran de connexion
            loadLoginScreen(primaryStage);

            // Affichage de la fenêtre
            primaryStage.show();

            logger.info("Application démarrée avec succès");

        } catch (Exception e) {
            logger.error("Erreur lors du démarrage de l'application", e);
            showStartupError(e);
            Platform.exit();
        }
    }

    @Override
    public void stop() throws Exception {
        logger.info("=== ARRÊT DE L'APPLICATION ===");

        try {
            // Nettoyage des ressources
            cleanupResources();
            logger.info("Nettoyage des ressources: OK");

        } catch (Exception e) {
            logger.error("Erreur lors de l'arrêt de l'application", e);
        } finally {
            super.stop();
            logger.info("Application fermée");
        }
    }

    /**
     * Configuration du thème AtlantaFX
     */
    private void setupTheme() {
        try {
            // Application du thème Primer Light par défaut
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

            logger.info("Thème AtlantaFX appliqué: Primer Light");

        } catch (Exception e) {
            logger.warn("Impossible d'appliquer le thème AtlantaFX, utilisation du thème par défaut", e);
        }
    }

    /**
     * Configuration de la fenêtre principale
     */
    private void setupPrimaryStage(Stage primaryStage) {
        // Titre de l'application
        primaryStage.setTitle(APP_TITLE + " - v" + APP_VERSION);

        // Icône de l'application
        try {
            Image icon = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream(APP_ICON)));
            primaryStage.getIcons().add(icon);
        } catch (Exception e) {
            logger.warn("Impossible de charger l'icône de l'application: {}", APP_ICON);
        }

        // Configuration de fermeture
        primaryStage.setOnCloseRequest(event -> {
            logger.info("Demande de fermeture de l'application");
            handleApplicationExit();
        });

        // Centrage sur l'écran
        primaryStage.centerOnScreen();
    }

    /**
     * Chargement de l'écran de connexion
     */
    private void loadLoginScreen(Stage primaryStage) throws Exception {
        Scene loginScene = FXMLLoaderUtil.loadScene("view/login.fxml", LOGIN_WIDTH, LOGIN_HEIGHT);

        // Application des styles personnalisés
        String customStylesheet = Objects.requireNonNull(
                getClass().getResource("/css/main-styles.css")).toExternalForm();
        loginScene.getStylesheets().add(customStylesheet);

        // Configuration de la scène
        primaryStage.setScene(loginScene);
        primaryStage.setResizable(false);
        primaryStage.setWidth(LOGIN_WIDTH);
        primaryStage.setHeight(LOGIN_HEIGHT);

        logger.info("Écran de connexion chargé");
    }

    /**
     * Initialisation de la base de données
     */
    private void initializeDatabase() throws Exception {
        // Vérification et création des tables SQLite
        DatabaseConfig.initializeSQLite();

        // Test de connexion MySQL (optionnel)
        try {
            DatabaseConfig.testMySQLConnection();
            logger.info("Connexion MySQL: Disponible");
        } catch (Exception e) {
            logger.warn("Connexion MySQL: Non disponible (mode local uniquement)");
        }

        // Création des données initiales si nécessaire
        DatabaseConfig.createInitialData();
    }

    /**
     * Nettoyage des ressources avant fermeture
     */
    private void cleanupResources() {
        try {
            // Fermeture des connexions à la base de données
            DatabaseConfig.closeAllConnections();

            // Autres nettoyages si nécessaire

        } catch (Exception e) {
            logger.error("Erreur lors du nettoyage des ressources", e);
        }
    }

    /**
     * Gestion de la fermeture de l'application
     */
    private void handleApplicationExit() {
        // TODO: Vérifier s'il y a des modifications non sauvegardées
        // TODO: Effectuer la synchronisation si nécessaire

        Platform.exit();
    }

    /**
     * Affichage d'une erreur de démarrage
     */
    private void showStartupError(Exception e) {
        try {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Erreur de démarrage");
            alert.setHeaderText("Impossible de démarrer l'application");
            alert.setContentText("Une erreur critique s'est produite lors du démarrage.\n\n" +
                    "Détails: " + e.getMessage() + "\n\n" +
                    "Veuillez consulter les logs pour plus d'informations.");

            alert.showAndWait();

        } catch (Exception alertException) {
            // En cas d'erreur lors de l'affichage de l'alerte
            logger.error("Impossible d'afficher l'alerte d'erreur", alertException);
            System.err.println("ERREUR CRITIQUE: " + e.getMessage());
        }
    }

    /**
     * Point d'entrée de l'application
     */
    public static void main(String[] args) {
        try {
            // Configuration des propriétés système si nécessaire
            setupSystemProperties();

            // Lancement de l'application JavaFX
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
        // Configuration du logging
        if (System.getProperty("logback.configurationFile") == null) {
            System.setProperty("logback.configurationFile", "logback.xml");
        }

        // Configuration JavaFX
        System.setProperty("javafx.preloader", "false");

        // Configuration pour Windows (si nécessaire)
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            System.setProperty("glass.accessible.force", "false");
        }

        // Configuration de l'accélération graphique
        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.subpixeltext", "false");
    }

    /**
     * Méthode utilitaire pour redémarrer l'application
     */
    public static void restart() {
        Platform.runLater(() -> {
            try {
                Stage currentStage = null; // TODO: Récupérer la stage courante
                if (currentStage != null) {
                    currentStage.close();
                }

                new Main().start(new Stage());

            } catch (Exception e) {
                logger.error("Erreur lors du redémarrage de l'application", e);
            }
        });
    }

    /**
     * @return Le titre de l'application
     */
    public static String getAppTitle() {
        return APP_TITLE;
    }

    /**
     * @return La version de l'application
     */
    public static String getAppVersion() {
        return APP_VERSION;
    }

    /**
     * @return Le titre complet de l'application avec version
     */
    public static String getFullAppTitle() {
        return APP_TITLE + " - v" + APP_VERSION;
    }
}