package com.regulation.contentieux.util;

import javafx.scene.Scene;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;

/**
 * Utilitaire pour charger les fichiers FXML - VERSION CORRIGÉE
 */
public class FXMLLoaderUtil {
    private static final Logger logger = LoggerFactory.getLogger(FXMLLoaderUtil.class);

    /**
     * Charge une scène FXML - CORRECTION DU BUG
     */
    public static Scene loadScene(String fxmlPath, double width, double height) {
        logger.info("=== DEBUG CHARGEMENT FXML ===");
        logger.info("Chemin demandé: {}", fxmlPath);

        try {
            // CORRECTION: Utiliser getResource avec le bon chemin
            URL fxmlUrl = FXMLLoaderUtil.class.getResource("/" + fxmlPath);
            logger.info("Méthode 1 (getResource /{}): {}", fxmlPath, fxmlUrl);

            if (fxmlUrl == null) {
                // Fallback: essayer sans le slash initial
                fxmlUrl = FXMLLoaderUtil.class.getClassLoader().getResource(fxmlPath);
                logger.info("Méthode 2 (ClassLoader {}): {}", fxmlPath, fxmlUrl);
            }

            if (fxmlUrl == null) {
                throw new RuntimeException("Fichier FXML introuvable: " + fxmlPath +
                        "\nVérifiez que le fichier existe dans src/main/resources/" + fxmlPath);
            }

            logger.info("✅ Chargement avec l'URL: {}", fxmlUrl);
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
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
     * Charge un fichier FXML et retourne le Parent
     * MÉTHODE MANQUANTE AJOUTÉE
     *
     * @param fxmlPath Chemin vers le fichier FXML
     * @return Parent chargé depuis le FXML
     */
    public static Parent loadFXML(String fxmlPath) {
        logger.debug("=== CHARGEMENT FXML ===");
        logger.debug("Chemin demandé: {}", fxmlPath);

        try {
            // Normaliser le chemin (enlever le "/" initial s'il existe)
            String normalizedPath = fxmlPath.startsWith("/") ? fxmlPath.substring(1) : fxmlPath;

            // CORRECTION: Utiliser getResource avec le bon chemin
            URL fxmlUrl = FXMLLoaderUtil.class.getResource("/" + normalizedPath);
            logger.debug("Méthode 1 (getResource /{}): {}", normalizedPath, fxmlUrl);

            if (fxmlUrl == null) {
                // Fallback: essayer sans le slash initial
                fxmlUrl = FXMLLoaderUtil.class.getClassLoader().getResource(normalizedPath);
                logger.debug("Méthode 2 (ClassLoader {}): {}", normalizedPath, fxmlUrl);
            }

            if (fxmlUrl == null) {
                String errorMsg = "Fichier FXML introuvable: " + fxmlPath +
                        "\nChemin normalisé: " + normalizedPath +
                        "\nVérifiez que le fichier existe dans src/main/resources/" + normalizedPath;
                logger.error("❌ {}", errorMsg);
                throw new RuntimeException(errorMsg);
            }

            logger.debug("✅ Chargement avec l'URL: {}", fxmlUrl);
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            logger.debug("✅ Fichier FXML chargé avec succès: {}", fxmlPath);
            return root;

        } catch (IOException e) {
            String errorMsg = "Erreur IOException lors du chargement du FXML: " + fxmlPath + " - " + e.getMessage();
            logger.error("❌ {}", errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        } catch (Exception e) {
            String errorMsg = "Erreur générale lors du chargement du FXML: " + fxmlPath + " - " + e.getMessage();
            logger.error("❌ {}", errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    /**
     * Charge un Parent FXML
     */
    public static Parent loadParent(String fxmlPath) {
        try {
            URL fxmlUrl = FXMLLoaderUtil.class.getResource("/" + fxmlPath);

            if (fxmlUrl == null) {
                fxmlUrl = FXMLLoaderUtil.class.getClassLoader().getResource(fxmlPath);
            }

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
     * CORRECTION BUG : Méthode utilitaire pour vérifier l'existence d'un fichier FXML
     */
    public static boolean existsFXML(String fxmlPath) {
        try {
            String normalizedPath = fxmlPath.startsWith("/") ? fxmlPath.substring(1) : fxmlPath;

            URL fxmlUrl = FXMLLoaderUtil.class.getResource("/" + normalizedPath);
            if (fxmlUrl == null) {
                fxmlUrl = FXMLLoaderUtil.class.getClassLoader().getResource(normalizedPath);
            }

            boolean exists = fxmlUrl != null;
            logger.debug("Vérification existence FXML {}: {}", fxmlPath, exists);
            return exists;

        } catch (Exception e) {
            logger.debug("Erreur lors de la vérification FXML {}: {}", fxmlPath, e.getMessage());
            return false;
        }
    }

    /**
     * CORRECTION BUG : Méthode de debug pour lister les ressources disponibles
     */
    public static void debugAvailableResources() {
        logger.debug("=== DEBUG RESSOURCES FXML ===");

        String[] commonPaths = {
                "fxml/login.fxml",
                "fxml/main.fxml",
                "/fxml/login.fxml",
                "/fxml/main.fxml"
        };

        for (String path : commonPaths) {
            URL resource = FXMLLoaderUtil.class.getResource(path);
            logger.debug("Ressource {}: {}", path, resource != null ? "TROUVÉE" : "NON TROUVÉE");
        }

        logger.debug("=== FIN DEBUG RESSOURCES ===");
    }

    /**
     * Charge un FXMLLoader avec le contrôleur
     */
    public static FXMLLoader createLoader(String fxmlPath) {
        URL fxmlUrl = FXMLLoaderUtil.class.getResource("/" + fxmlPath);

        if (fxmlUrl == null) {
            fxmlUrl = FXMLLoaderUtil.class.getClassLoader().getResource(fxmlPath);
        }

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
     * Vérifie si un fichier FXML existe
     */
    public static boolean fxmlExists(String fxmlPath) {
        URL fxmlUrl = FXMLLoaderUtil.class.getResource("/" + fxmlPath);
        if (fxmlUrl == null) {
            fxmlUrl = FXMLLoaderUtil.class.getClassLoader().getResource(fxmlPath);
        }
        return fxmlUrl != null;
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