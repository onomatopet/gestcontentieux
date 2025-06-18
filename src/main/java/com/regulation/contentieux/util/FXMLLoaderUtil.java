package com.regulation.contentieux.util;

import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;

/**
 * Utilitaire pour charger les fichiers FXML avec gestion d'erreurs robuste
 */
public class FXMLLoaderUtil {
    private static final Logger logger = LoggerFactory.getLogger(FXMLLoaderUtil.class);

    /**
     * Charge une scène FXML avec debug détaillé
     */
    public static Scene loadScene(String fxmlPath, double width, double height) {
        logger.info("=== DEBUG CHARGEMENT FXML ===");
        logger.info("Chemin demandé: {}", fxmlPath);

        try {
            // Méthode 1: Via getResource direct
            URL fxmlUrl1 = FXMLLoaderUtil.class.getResource("/" + fxmlPath);
            logger.info("Méthode 1 (getResource /{}): {}", fxmlPath, fxmlUrl1);

            // Méthode 2: Via ClassLoader
            URL fxmlUrl2 = FXMLLoaderUtil.class.getClassLoader().getResource(fxmlPath);
            logger.info("Méthode 2 (ClassLoader {}): {}", fxmlPath, fxmlUrl2);

            // Méthode 3: Test des variations de chemin
            String[] pathVariations = {
                    fxmlPath,
                    "/" + fxmlPath,
                    "resources/" + fxmlPath,
                    "/resources/" + fxmlPath
            };

            URL workingUrl = null;
            for (String path : pathVariations) {
                URL testUrl = FXMLLoaderUtil.class.getClassLoader().getResource(path);
                logger.info("Test chemin '{}': {}", path, testUrl);
                if (testUrl != null && workingUrl == null) {
                    workingUrl = testUrl;
                    logger.info("✅ CHEMIN TROUVÉ: {}", path);
                }
            }

            if (workingUrl == null) {
                // Lister les ressources disponibles
                logger.error("❌ AUCUN CHEMIN TROUVÉ. Listing des ressources:");

                // Essayer de lister le contenu du dossier resources
                try {
                    URL resourcesUrl = FXMLLoaderUtil.class.getClassLoader().getResource("");
                    if (resourcesUrl != null) {
                        logger.info("Dossier resources trouvé à: {}", resourcesUrl);
                    } else {
                        logger.error("Dossier resources non trouvé !");
                    }
                } catch (Exception e) {
                    logger.error("Erreur lors du listing des ressources", e);
                }

                throw new RuntimeException("Fichier FXML introuvable: " + fxmlPath +
                        "\nVérifiez que le fichier existe dans src/main/resources/" + fxmlPath);
            }

            logger.info("Chargement avec l'URL: {}", workingUrl);
            FXMLLoader loader = new FXMLLoader(workingUrl);
            Parent root = loader.load();

            logger.info("✅ Fichier FXML chargé avec succès !");
            return new Scene(root, width, height);

        } catch (IOException e) {
            logger.error("❌ Erreur IOException lors du chargement du FXML: " + fxmlPath, e);
            throw new RuntimeException("Erreur de chargement FXML: " + fxmlPath + " - " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("❌ Erreur générale lors du chargement du FXML: " + fxmlPath, e);
            throw new RuntimeException("Erreur inattendue lors du chargement FXML: " + fxmlPath, e);
        }
    }

    /**
     * Charge un Parent FXML
     */
    public static Parent loadParent(String fxmlPath) {
        try {
            URL fxmlUrl = findFXMLResource(fxmlPath);

            if (fxmlUrl == null) {
                logger.error("Fichier FXML introuvable: {}", fxmlPath);
                throw new RuntimeException("Fichier FXML introuvable: " + fxmlPath);
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent parent = loader.load();

            logger.info("Parent FXML chargé avec succès: {}", fxmlPath);
            return parent;

        } catch (IOException e) {
            logger.error("Erreur lors du chargement du Parent FXML: " + fxmlPath, e);
            throw new RuntimeException("Impossible de charger le fichier FXML: " + fxmlPath, e);
        }
    }

    /**
     * Charge un FXMLLoader avec le contrôleur
     */
    public static FXMLLoader createLoader(String fxmlPath) {
        URL fxmlUrl = findFXMLResource(fxmlPath);

        if (fxmlUrl == null) {
            logger.error("Fichier FXML introuvable: {}", fxmlPath);
            throw new RuntimeException("Fichier FXML introuvable: " + fxmlPath);
        }

        return new FXMLLoader(fxmlUrl);
    }

    /**
     * Charge un Parent FXML avec son contrôleur
     */
    public static <T> LoadResult<T> loadWithController(String fxmlPath) {
        try {
            FXMLLoader loader = createLoader(fxmlPath);
            Parent parent = loader.load();
            T controller = loader.getController();

            logger.info("FXML chargé avec contrôleur: {}", fxmlPath);
            return new LoadResult<>(parent, controller);

        } catch (IOException e) {
            logger.error("Erreur lors du chargement FXML avec contrôleur: " + fxmlPath, e);
            throw new RuntimeException("Impossible de charger le FXML avec contrôleur: " + fxmlPath, e);
        }
    }

    /**
     * Charge une scène FXML avec son contrôleur
     */
    public static <T> SceneResult<T> loadSceneWithController(String fxmlPath, double width, double height) {
        LoadResult<T> loadResult = loadWithController(fxmlPath);
        Scene scene = new Scene(loadResult.getParent(), width, height);

        return new SceneResult<>(scene, loadResult.getController());
    }

    /**
     * Trouve une ressource FXML en testant plusieurs chemins
     */
    private static URL findFXMLResource(String fxmlPath) {
        String[] pathVariations = {
                fxmlPath,
                "/" + fxmlPath,
                "view/" + fxmlPath,
                "/view/" + fxmlPath,
                "resources/" + fxmlPath,
                "/resources/" + fxmlPath
        };

        for (String path : pathVariations) {
            URL url = FXMLLoaderUtil.class.getClassLoader().getResource(path);
            if (url != null) {
                logger.debug("Ressource FXML trouvée: {} -> {}", path, url);
                return url;
            }
        }

        return null;
    }

    /**
     * Charge une vue FXML de manière sécurisée avec gestion d'erreurs
     */
    public static Parent loadSafely(String fxmlPath) {
        try {
            return loadParent(fxmlPath);
        } catch (Exception e) {
            logger.error("Échec du chargement sécurisé de: " + fxmlPath, e);

            // Retourner une vue d'erreur par défaut
            return createErrorView("Erreur de chargement",
                    "Impossible de charger la vue: " + fxmlPath,
                    e.getMessage());
        }
    }

    /**
     * Crée une vue d'erreur par défaut
     */
    private static Parent createErrorView(String title, String message, String details) {
        javafx.scene.layout.VBox errorView = new javafx.scene.layout.VBox(10);
        errorView.setAlignment(javafx.geometry.Pos.CENTER);
        errorView.setPadding(new javafx.geometry.Insets(20));

        javafx.scene.control.Label titleLabel = new javafx.scene.control.Label(title);
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: red;");

        javafx.scene.control.Label messageLabel = new javafx.scene.control.Label(message);
        messageLabel.setStyle("-fx-font-size: 14px;");
        messageLabel.setWrapText(true);

        javafx.scene.control.Label detailsLabel = new javafx.scene.control.Label(details);
        detailsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: gray;");
        detailsLabel.setWrapText(true);

        errorView.getChildren().addAll(titleLabel, messageLabel, detailsLabel);
        return errorView;
    }

    /**
     * Vérifie si un fichier FXML existe
     */
    public static boolean fxmlExists(String fxmlPath) {
        return findFXMLResource(fxmlPath) != null;
    }

    /**
     * Classe pour encapsuler le résultat du chargement avec contrôleur
     */
    public static class LoadResult<T> {
        private final Parent parent;
        private final T controller;

        public LoadResult(Parent parent, T controller) {
            this.parent = parent;
            this.controller = controller;
        }

        public Parent getParent() { return parent; }
        public T getController() { return controller; }
    }

    /**
     * Classe pour encapsuler le résultat du chargement de scène avec contrôleur
     */
    public static class SceneResult<T> {
        private final Scene scene;
        private final T controller;

        public SceneResult(Scene scene, T controller) {
            this.scene = scene;
            this.controller = controller;
        }

        public Scene getScene() { return scene; }
        public T getController() { return controller; }
    }
}