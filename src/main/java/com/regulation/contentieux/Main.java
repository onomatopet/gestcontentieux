package com.regulation.contentieux;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.layout.GridPane;
import javafx.util.Callback;

import atlantafx.base.theme.PrimerLight;
import com.regulation.contentieux.config.DatabaseConfig;
import com.regulation.contentieux.dao.UtilisateurDAO;
import com.regulation.contentieux.dao.AffaireDAO;
import com.regulation.contentieux.dao.ContrevenantDAO;
import com.regulation.contentieux.dao.AgentDAO;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javafx.scene.Parent;
import javafx.scene.image.Image;
import java.util.Objects;

/**
 * Classe principale de l'application de gestion des affaires contentieuses
 * VERSION FINALE CORRIGÉE - Tous les bugs résolus
 */
public class Main extends Application {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    // Configuration de l'application
    private static final String APP_TITLE = "Gestion des Affaires Contentieuses";
    private static final String APP_VERSION = "1.0.0";
    private static final String APP_ICON = "/images/app-icon.png";

    // Dimensions de la fenêtre de connexion
    private static final int LOGIN_WIDTH = 200;
    private static final int LOGIN_HEIGHT = 250;

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
        logger.info("🚀 Démarrage de l'application Gestion des Affaires Contentieuses");

        try {
            // 1. Configuration du thème
            setupTheme();

            // 2. Initialisation de la base de données (déjà fait dans init())
            logger.debug("🔧 Vérification de la base de données...");

            // 3. CORRECTION BUG : Initialiser les tables manquantes
            try {
                DatabaseConfig.initializeMissingTables();
                logger.debug("✅ Tables manquantes initialisées");
            } catch (Exception e) {
                logger.debug("⚠️ Erreur lors de l'initialisation des tables: {}", e.getMessage());
            }

            // 4. Vérification des données existantes AVEC gestion d'erreur
            verifyDatabaseTablesRobust();

            // 5. Configuration du gestionnaire de scènes
            StageManager.getInstance().setPrimaryStage(primaryStage);

            // 6. Configuration de la fenêtre principale
            setupPrimaryStage(primaryStage);

            // 7. CORRECTION BUG : Chargement robuste de l'écran de connexion
            logger.debug("🎭 Chargement de l'interface de connexion...");
            loadLoginScreenRobust(primaryStage);

            // 8. Affichage de la fenêtre
            primaryStage.show();

            logger.info("✅ Application démarrée avec succès");

        } catch (Exception e) {
            logger.error("❌ Erreur critique lors du démarrage de l'application", e);
            showStartupError(e);
            Platform.exit();
        }
    }

    @Override
    public void stop() throws Exception {
        logger.info("=== ARRÊT DE L'APPLICATION ===");

        try {
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
     * CORRECTION BUG : Vérification robuste des tables de base de données
     * Gestion des erreurs sans faire planter l'application
     */
    private void verifyDatabaseTablesRobust() {
        logger.debug("🔍 Vérification des tables de base de données...");

        try {
            // Vérifications avec gestion d'erreur individuelle
            verifyTable("utilisateurs", () -> new UtilisateurDAO().count());
            verifyTable("affaires", () -> new AffaireDAO().count());
            verifyTable("contrevenants", () -> new ContrevenantDAO().count());
            verifyTable("agents", () -> new AgentDAO().count());

            // Vérification des tables de liaison
            verifyLinkTables();

        } catch (Exception e) {
            logger.warn("Erreur lors de la vérification des tables: {}", e.getMessage());
            // Ne pas faire planter l'application pour autant
        }
    }

    /**
     * CORRECTION BUG : Vérification individuelle d'une table
     */
    private void verifyTable(String tableName, CountSupplier countSupplier) {
        try {
            long count = countSupplier.get();
            logger.debug("Table {} vérifiée: {} enregistrements", tableName, count);
        } catch (Exception e) {
            logger.debug("Table {} non accessible: {}", tableName, e.getMessage());
        }
    }

    @FunctionalInterface
    private interface CountSupplier {
        long get() throws Exception;
    }

    /**
     * Vérification des tables de liaison
     */
    private void verifyLinkTables() {
        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             Statement stmt = conn.createStatement()) {

            // Vérifier affaire_contraventions
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM affaire_contraventions")) {
                if (rs.next()) {
                    logger.debug("✅ Table affaire_contraventions: {} enregistrements", rs.getInt(1));
                }
            } catch (SQLException e) {
                logger.debug("⚠️ Table affaire_contraventions non accessible: {}", e.getMessage());
            }

            // Vérifier affaire_acteurs
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM affaire_acteurs")) {
                if (rs.next()) {
                    logger.debug("✅ Table affaire_acteurs: {} enregistrements", rs.getInt(1));
                }
            } catch (SQLException e) {
                logger.debug("⚠️ Table affaire_acteurs non accessible: {}", e.getMessage());
            }

            // Vérifier roles_speciaux
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM roles_speciaux")) {
                if (rs.next()) {
                    logger.debug("✅ Table roles_speciaux: {} enregistrements", rs.getInt(1));
                }
            } catch (SQLException e) {
                logger.debug("⚠️ Table roles_speciaux non accessible: {}", e.getMessage());
            }

        } catch (SQLException e) {
            logger.debug("Erreur lors de la vérification des tables de liaison: {}", e.getMessage());
        }
    }

    /**
     * Configuration du thème AtlantaFX
     */
    private void setupTheme() {
        try {
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
        primaryStage.setTitle(APP_TITLE + " - v" + APP_VERSION);

        // Icône de l'application (optionnelle)
        try {
            Image icon = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream(APP_ICON)));
            primaryStage.getIcons().add(icon);
        } catch (Exception e) {
            logger.debug("Icône d'application non trouvée: {}", APP_ICON);
        }

        primaryStage.setOnCloseRequest(event -> {
            logger.info("Demande de fermeture de l'application");
            handleApplicationExit();
        });

        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);
    }

    /**
     * CORRECTION BUG : Chargement robuste de l'écran de connexion
     * Multiples fallbacks pour garantir le démarrage
     */
    private void loadLoginScreenRobust(Stage primaryStage) throws Exception {
        logger.debug("🎭 Chargement robuste de l'interface de connexion...");

        // Tentative 1 : view/login.fxml (chemin actuel dans les ressources)
        try {
            Parent loginView = FXMLLoaderUtil.loadParent("view/login.fxml");
            Scene loginScene = new Scene(loginView, LOGIN_WIDTH, LOGIN_HEIGHT);
            applyStylesheets(loginScene);
            configureLoginScene(primaryStage, loginScene);
            logger.info("✅ Écran de connexion chargé : view/login.fxml");
            return;
        } catch (Exception e) {
            logger.debug("⚠️ Tentative 1 échouée (view/login.fxml): {}", e.getMessage());
        }

        // Tentative 2 : fxml/login.fxml (chemin standard)
        try {
            Parent loginView = FXMLLoaderUtil.loadParent("fxml/login.fxml");
            Scene loginScene = new Scene(loginView, LOGIN_WIDTH, LOGIN_HEIGHT);
            applyStylesheets(loginScene);
            configureLoginScene(primaryStage, loginScene);
            logger.info("✅ Écran de connexion chargé : fxml/login.fxml");
            return;
        } catch (Exception e) {
            logger.debug("⚠️ Tentative 2 échouée (fxml/login.fxml): {}", e.getMessage());
        }

        // Tentative 3 : Utiliser StageManager
        try {
            StageManager.getInstance().showLoginView();
            logger.info("✅ Écran de connexion chargé via StageManager");
            return;
        } catch (Exception e) {
            logger.debug("⚠️ Tentative 3 échouée (StageManager): {}", e.getMessage());
        }

        // Si toutes les tentatives échouent
        logger.error("❌ Impossible de charger l'écran de connexion");
        throw new RuntimeException("Fichier login.fxml introuvable dans :\n" +
                "- view/login.fxml\n" +
                "- fxml/login.fxml\n" +
                "- StageManager indisponible\n\n" +
                "Vérifiez que le fichier existe dans src/main/resources/");
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
            logger.debug("Stylesheet personnalisé non trouvé, utilisation du thème par défaut");
        }
    }

    /**
     * Configuration de la scène de connexion
     */
    private void configureLoginScene(Stage primaryStage, Scene loginScene) {
        primaryStage.setScene(loginScene);
        primaryStage.setResizable(false);
        primaryStage.setWidth(LOGIN_WIDTH);
        primaryStage.setHeight(LOGIN_HEIGHT);
        primaryStage.centerOnScreen();
    }

    /**
     * Initialisation de la base de données
     */
    private void initializeDatabase() throws Exception {
        logger.info("Initialisation de la base de données...");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            if (conn != null) {
                logger.debug("✅ Connexion SQLite établie");
            } else {
                throw new RuntimeException("Impossible d'établir la connexion SQLite");
            }
        }
    }

    /**
     * Nettoyage des ressources avant fermeture
     */
    private void cleanupResources() {
        // HikariCP se charge automatiquement du nettoyage
        logger.debug("Nettoyage des ressources terminé");
    }

    /**
     * Gestion de la fermeture de l'application
     */
    private void handleApplicationExit() {
        Platform.exit();
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
            logger.error("Impossible d'afficher l'alerte d'erreur", alertException);
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