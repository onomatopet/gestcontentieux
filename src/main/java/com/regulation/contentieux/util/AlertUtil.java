package com.regulation.contentieux.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

/**
 * Utilitaire pour l'affichage des alertes
 * Centralise la gestion des dialogues dans l'application
 */
public class AlertUtil {

    private static final Logger logger = LoggerFactory.getLogger(AlertUtil.class);

    /**
     * Affiche une alerte d'information
     */
    public static void showInfoAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
        logger.info("Info alert shown: {}", header);
    }

    /**
     * Affiche une alerte de succès
     */
    public static void showSuccessAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        // Ajouter un style CSS pour le succès si disponible
        alert.getDialogPane().getStyleClass().add("success-alert");

        alert.showAndWait();
        logger.info("Success alert shown: {}", header);
    }

    /**
     * Affiche une alerte d'avertissement
     */
    public static void showWarningAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
        logger.warn("Warning alert shown: {}", header);
    }

    /**
     * Affiche une alerte d'erreur
     */
    public static void showErrorAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
        logger.error("Error alert shown: {}", header);
    }

    /**
     * Affiche une alerte d'erreur avec détails d'exception
     */
    public static void showExceptionAlert(String title, String header, Exception exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(exception.getMessage());

        // Créer la zone de texte extensible pour les détails
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);
        String exceptionText = sw.toString();

        TextArea textArea = new TextArea(exceptionText);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);

        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        GridPane expContent = new GridPane();
        expContent.setMaxWidth(Double.MAX_VALUE);
        expContent.add(textArea, 0, 0);

        // Définir le contenu extensible
        alert.getDialogPane().setExpandableContent(expContent);

        alert.showAndWait();
        logger.error("Exception alert shown: {}", header, exception);
    }

    /**
     * Affiche une alerte de confirmation (Oui/Non)
     * Méthode ajoutée pour corriger l'erreur de compilation
     */
    public static boolean showConfirmAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        // Personnaliser les boutons
        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

        Optional<ButtonType> result = alert.showAndWait();
        boolean confirmed = result.isPresent() && result.get() == ButtonType.YES;

        logger.info("Confirmation alert shown: {} - Result: {}", header, confirmed);
        return confirmed;
    }

    /**
     * Affiche une alerte de confirmation personnalisée
     * Ancienne méthode maintenue pour compatibilité
     */
    public static boolean showConfirmationAlert(String title, String header, String content) {
        return showConfirmAlert(title, header, content);
    }

    /**
     * Affiche une alerte de confirmation avec options personnalisées
     */
    public static Optional<ButtonType> showCustomConfirmAlert(String title, String header,
                                                              String content, ButtonType... buttons) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        if (buttons.length > 0) {
            alert.getButtonTypes().setAll(buttons);
        }

        Optional<ButtonType> result = alert.showAndWait();
        logger.info("Custom confirmation alert shown: {} - Result: {}",
                header, result.map(ButtonType::getText).orElse("None"));

        return result;
    }

    /**
     * Affiche une alerte avec un message long
     */
    public static void showLongMessageAlert(String title, String header, String longMessage) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);

        // Pour les messages longs, utiliser une TextArea
        TextArea textArea = new TextArea(longMessage);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);

        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        GridPane content = new GridPane();
        content.setMaxWidth(Double.MAX_VALUE);
        content.add(textArea, 0, 0);

        alert.getDialogPane().setContent(content);
        alert.showAndWait();

        logger.info("Long message alert shown: {}", header);
    }

    /**
     * Affiche une alerte de progression (sans boutons)
     * Note: Cette alerte doit être fermée manuellement
     */
    public static Alert showProgressAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        // Supprimer tous les boutons
        alert.getButtonTypes().clear();

        // Ajouter un style pour l'indicateur de progression si disponible
        alert.getDialogPane().getStyleClass().add("progress-alert");

        alert.show();
        logger.info("Progress alert shown: {}", header);

        return alert;
    }

    // ================= MÉTHODES SIMPLIFIÉES AJOUTÉES =================
    // Ces méthodes sont ajoutées pour compatibilité avec MandatController

    /**
     * Version simplifiée de showWarningAlert pour MandatController
     */
    public static void showWarning(String title, String content) {
        showWarningAlert(title, title, content);
    }

    /**
     * Version simplifiée de showSuccessAlert pour MandatController
     */
    public static void showSuccess(String title, String content) {
        showSuccessAlert(title, title, content);
    }

    /**
     * Version simplifiée de showErrorAlert pour MandatController
     * avec 3 paramètres
     */
    public static void showError(String title, String header, String content) {
        showErrorAlert(title, header, content);
    }

    /**
     * Version simplifiée de showInfoAlert pour MandatController
     */
    public static void showInfo(String title, String content) {
        showInfoAlert(title, title, content);
    }
}