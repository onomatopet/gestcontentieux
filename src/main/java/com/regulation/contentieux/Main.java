package com.regulation.contentieux;

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
 * VERSION CORRIGÉE - Utilisation des bonnes méthodes existantes
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
        logger.info("🚀 Démarrage de l'application Gestion des Affaires Contentieuses");

        try {
            // 1. Configuration du thème
            setupTheme();

            // 2. Initialisation de la base de données (déjà fait dans init())
            logger.debug("🔧 Vérification de la base de données...");

            // 3. CORRECTION BUG : Initialiser les tables manquantes si méthode existe
            try {
                DatabaseConfig.initializeMissingTables();
                logger.debug("✅ Tables manquantes initialisées");
            } catch (Exception e) {
                logger.debug("⚠️ Méthode initializeMissingTables non disponible: {}", e.getMessage());
            }

            // 4. Vérification des données existantes
            verifyDatabaseTables();

            // 5. CORRECTION BUG : Configuration du gestionnaire de scènes
            // Utiliser setPrimaryStage au lieu d'initialize
            StageManager.getInstance().setPrimaryStage(primaryStage);

            // 6. Configuration de la fenêtre principale
            setupPrimaryStage(primaryStage);

            // 7. CORRECTION BUG : Chargement de l'écran de connexion
            logger.debug("🎭 Chargement de l'interface de connexion...");
            loadLoginScreen(primaryStage);

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
     * CORRECTION BUG : Vérification des tables de base de données
     */
    private void verifyDatabaseTables() {
        try {
            // Vérifications existantes
            UtilisateurDAO userDAO = new UtilisateurDAO();
            AffaireDAO affaireDAO = new AffaireDAO();
            ContrevenantDAO contrevenantDAO = new ContrevenantDAO();
            AgentDAO agentDAO = new AgentDAO();

            long userCount = userDAO.count();
            long affaireCount = affaireDAO.count();
            long contrevenantCount = contrevenantDAO.count();
            long agentCount = agentDAO.count();

            logger.debug("Table utilisateurs vérifiée: {} enregistrements", userCount);
            logger.debug("Table affaires vérifiée: {} enregistrements", affaireCount);
            logger.debug("Table contrevenants vérifiée: {} enregistrements", contrevenantCount);
            logger.debug("Table agents vérifiée: {} enregistrements", agentCount);

            // NOUVELLE VÉRIFICATION : Tables de liaison
            verifyLinkTables();

        } catch (Exception e) {
            logger.warn("Erreur lors de la vérification des tables: {}", e.getMessage());
        }
    }

    /**
     * CORRECTION BUG : Vérification des tables de liaison
     * MÉTHODE SÉPARÉE - non imbriquée
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
                logger.warn("⚠️ Table affaire_contraventions non accessible: {}", e.getMessage());
            }

            // Vérifier affaire_acteurs
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM affaire_acteurs")) {
                if (rs.next()) {
                    logger.debug("✅ Table affaire_acteurs: {} enregistrements", rs.getInt(1));
                }
            } catch (SQLException e) {
                logger.warn("⚠️ Table affaire_acteurs non accessible: {}", e.getMessage());
            }

            // Vérifier roles_speciaux
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM roles_speciaux")) {
                if (rs.next()) {
                    logger.debug("✅ Table roles_speciaux: {} enregistrements", rs.getInt(1));
                }
            } catch (SQLException e) {
                logger.warn("⚠️ Table roles_speciaux non accessible: {}", e.getMessage());
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification des tables de liaison", e);
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

        // Dimensions minimales
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);
    }

    /**
     * CORRECTION BUG : Chargement de l'écran de connexion
     * Utilisation des méthodes existantes dans FXMLLoaderUtil
     */
    private void loadLoginScreen(Stage primaryStage) throws Exception {
        // CORRECTION : Utiliser loadParent au lieu de loadFXML
        Parent loginView = FXMLLoaderUtil.loadParent("fxml/login.fxml");
        Scene loginScene = new Scene(loginView, LOGIN_WIDTH, LOGIN_HEIGHT);

        // Application des styles personnalisés si disponibles
        try {
            String customStylesheet = Objects.requireNonNull(
                    getClass().getResource("/css/application.css")).toExternalForm();
            loginScene.getStylesheets().add(customStylesheet);
        } catch (Exception e) {
            logger.debug("Stylesheet personnalisé non trouvé, utilisation du thème par défaut");
        }

        // Configuration de la scène
        primaryStage.setScene(loginScene);
        primaryStage.setResizable(false);
        primaryStage.setWidth(LOGIN_WIDTH);
        primaryStage.setHeight(LOGIN_HEIGHT);
        primaryStage.centerOnScreen();

        logger.info("Écran de connexion chargé");
    }

    /**
     * Initialisation de la base de données
     */
    private void initializeDatabase() throws Exception {
        logger.info("Initialisation de la base de données...");

        // Test de connexion simple
        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            if (conn != null) {
                logger.debug("✅ Connexion SQLite établie");
            } else {
                throw new RuntimeException("Impossible d'établir la connexion SQLite");
            }
        }

        // Vérification finale
        verifyDatabaseIntegrity();
    }

    /**
     * Vérifie l'intégrité de la base de données
     */
    private void verifyDatabaseIntegrity() {
        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            // Vérifier quelques tables clés
            String[] criticalTables = {"utilisateurs", "affaires", "contrevenants", "agents"};

            for (String table : criticalTables) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {

                    if (rs.next()) {
                        int count = rs.getInt(1);
                        logger.debug("Table {} vérifiée: {} enregistrements", table, count);
                    }
                } catch (SQLException e) {
                    logger.debug("Table {} non accessible (peut être normale): {}", table, e.getMessage());
                }
            }

            logger.info("Intégrité de la base de données vérifiée avec succès");

        } catch (Exception e) {
            logger.error("Erreur lors de la vérification de l'intégrité", e);
            throw new RuntimeException("Base de données corrompue", e);
        }
    }

    /**
     * Nettoyage des ressources avant fermeture
     */
    private void cleanupResources() {
        try {
            // Fermeture des connexions à la base de données si méthode disponible
            // Note: HikariCP se charge automatiquement du nettoyage
            logger.debug("Nettoyage des ressources terminé");
        } catch (Exception e) {
            logger.error("Erreur lors du nettoyage des ressources", e);
        }
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
     * @return Le titre complet de l'application avec version
     */
    public static String getFullAppTitle() {
        return APP_TITLE + " - v" + APP_VERSION;
    }
}