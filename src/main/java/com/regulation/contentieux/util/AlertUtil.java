package com.regulation.contentieux.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar; // CORRECTION : Import manquant
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

    // ================= MÉTHODES PRINCIPALES ENRICHIES =================

    /**
     * ENRICHISSEMENT : Affiche une alerte d'information avec header
     */
    public static void showInfoAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        // ENRICHISSEMENT : Amélioration de l'apparence
        alert.getDialogPane().getStyleClass().add("info-alert");

        alert.showAndWait();
        logger.info("Info alert shown: {}", header);
    }

    /**
     * ENRICHISSEMENT : Affiche une alerte de succès
     */
    public static void showSuccessAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        // ENRICHISSEMENT : Ajouter un style CSS pour le succès
        alert.getDialogPane().getStyleClass().add("success-alert");

        alert.showAndWait();
        logger.info("Success alert shown: {}", header);
    }

    /**
     * ENRICHISSEMENT : Affiche une alerte d'avertissement avec header
     */
    public static void showWarningAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        // ENRICHISSEMENT : Style d'avertissement
        alert.getDialogPane().getStyleClass().add("warning-alert");

        alert.showAndWait();
        logger.warn("Warning alert shown: {}", header);
    }

    /**
     * ENRICHISSEMENT : Affiche une alerte d'erreur avec header
     */
    public static void showErrorAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        // ENRICHISSEMENT : Style d'erreur
        alert.getDialogPane().getStyleClass().add("error-alert");

        alert.showAndWait();
        logger.error("Error alert shown: {}", header);
    }

    /**
     * ENRICHISSEMENT : Affiche une alerte d'erreur avec détails d'exception
     */
    public static void showExceptionAlert(String title, String header, Exception exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(exception.getMessage());

        // ENRICHISSEMENT : Créer la zone de texte extensible pour les détails
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
        alert.getDialogPane().getStyleClass().add("exception-alert");

        alert.showAndWait();
        logger.error("Exception alert shown: {}", header, exception);
    }

    // ================= MÉTHODES DE CONFIRMATION ENRICHIES =================

    /**
     * ENRICHISSEMENT : Affiche une alerte de confirmation (Oui/Non)
     */
    public static boolean showConfirmAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        // ENRICHISSEMENT : Personnaliser les boutons
        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        alert.getDialogPane().getStyleClass().add("confirm-alert");

        Optional<ButtonType> result = alert.showAndWait();
        boolean confirmed = result.isPresent() && result.get() == ButtonType.YES;

        logger.info("Confirmation alert shown: {} - Result: {}", header, confirmed);
        return confirmed;
    }

    /**
     * CORRECTION BUG : Méthode de confirmation simple sans header
     */
    public static boolean showConfirmation(String titre, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(titre);
        alert.setHeaderText(null);
        alert.setContentText(message);

        // ENRICHISSEMENT : Personnaliser les boutons en français
        ButtonType buttonOui = new ButtonType("Oui", ButtonBar.ButtonData.YES);
        ButtonType buttonNon = new ButtonType("Non", ButtonBar.ButtonData.NO);
        alert.getButtonTypes().setAll(buttonOui, buttonNon);

        // ENRICHISSEMENT : Style
        alert.getDialogPane().getStyleClass().add("confirmation-alert");

        // Afficher et attendre la réponse
        Optional<ButtonType> result = alert.showAndWait();
        boolean confirmed = result.isPresent() && result.get() == buttonOui;

        logger.info("Confirmation simple shown: {} - Result: {}", titre, confirmed);
        return confirmed;
    }

    /**
     * ENRICHISSEMENT : Confirmation avec message détaillé et header
     */
    public static boolean showConfirmation(String titre, String headerText, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(titre);
        alert.setHeaderText(headerText);
        alert.setContentText(message);

        // ENRICHISSEMENT : Boutons en français
        ButtonType buttonOui = new ButtonType("Oui", ButtonBar.ButtonData.YES);
        ButtonType buttonNon = new ButtonType("Non", ButtonBar.ButtonData.NO);
        alert.getButtonTypes().setAll(buttonOui, buttonNon);

        alert.getDialogPane().getStyleClass().add("confirmation-detailed-alert");

        Optional<ButtonType> result = alert.showAndWait();
        boolean confirmed = result.isPresent() && result.get() == buttonOui;

        logger.info("Confirmation détaillée shown: {} - Result: {}", titre, confirmed);
        return confirmed;
    }

    /**
     * Ancienne méthode maintenue pour compatibilité
     */
    public static boolean showConfirmationAlert(String title, String header, String content) {
        return showConfirmAlert(title, header, content);
    }

    /**
     * ENRICHISSEMENT : Affiche une alerte de confirmation avec options personnalisées
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

        alert.getDialogPane().getStyleClass().add("custom-confirm-alert");

        Optional<ButtonType> result = alert.showAndWait();
        logger.info("Custom confirmation alert shown: {} - Result: {}",
                header, result.map(ButtonType::getText).orElse("None"));

        return result;
    }

    /**
     * ENRICHISSEMENT : Confirmation avec choix personnalisés
     */
    public static Optional<ButtonType> showConfirmationWithOptions(String titre, String message, ButtonType... options) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(titre);
        alert.setHeaderText(null);
        alert.setContentText(message);

        if (options.length > 0) {
            alert.getButtonTypes().setAll(options);
        }

        alert.getDialogPane().getStyleClass().add("confirmation-options-alert");

        Optional<ButtonType> result = alert.showAndWait();
        logger.info("Confirmation avec options shown: {} - Result: {}",
                titre, result.map(ButtonType::getText).orElse("None"));

        return result;
    }

    // ================= MÉTHODES SIMPLIFIÉES (2 PARAMÈTRES) =================

    /**
     * CORRECTION : Version simplifiée de showError SANS DOUBLON
     */
    public static void showError(String titre, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(titre);
        alert.setHeaderText(null);
        alert.setContentText(message);

        // ENRICHISSEMENT : Style d'erreur simple
        alert.getDialogPane().getStyleClass().add("simple-error-alert");

        alert.showAndWait();
        logger.error("Simple error alert shown: {} - {}", titre, message);
    }

    /**
     * CORRECTION : Version simplifiée de showWarning SANS DOUBLON
     */
    public static void showWarning(String titre, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(titre);
        alert.setHeaderText(null);
        alert.setContentText(message);

        // ENRICHISSEMENT : Style d'avertissement simple
        alert.getDialogPane().getStyleClass().add("simple-warning-alert");

        alert.showAndWait();
        logger.warn("Simple warning alert shown: {} - {}", titre, message);
    }

    /**
     * CORRECTION : Version simplifiée de showInfo SANS DOUBLON
     */
    public static void showInfo(String titre, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titre);
        alert.setHeaderText(null);
        alert.setContentText(message);

        // ENRICHISSEMENT : Style d'information simple
        alert.getDialogPane().getStyleClass().add("simple-info-alert");

        alert.showAndWait();
        logger.info("Simple info alert shown: {} - {}", titre, message);
    }

    /**
     * ENRICHISSEMENT : Version simplifiée de showSuccess
     */
    public static void showSuccess(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);

        // ENRICHISSEMENT : Style de succès simple
        alert.getDialogPane().getStyleClass().add("simple-success-alert");

        alert.showAndWait();
        logger.info("Simple success alert shown: {} - {}", title, content);
    }

    // ================= MÉTHODES AVEC 3 PARAMÈTRES =================

    /**
     * ENRICHISSEMENT : Version à 3 paramètres de showError
     */
    public static void showError(String title, String header, String content) {
        showErrorAlert(title, header, content);
    }

    /**
     * ENRICHISSEMENT : Version à 3 paramètres avec Exception
     */
    public static void showError(String title, String content, Exception exception) {
        showExceptionAlert(title, content, exception);
    }

    // ================= MÉTHODES AVANCÉES ENRICHIES =================

    /**
     * ENRICHISSEMENT : Affiche une alerte avec un message long
     */
    public static void showLongMessageAlert(String title, String header, String longMessage) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);

        // ENRICHISSEMENT : Pour les messages longs, utiliser une TextArea améliorée
        TextArea textArea = new TextArea(longMessage);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        textArea.setPrefRowCount(10);
        textArea.setPrefColumnCount(50);

        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        GridPane content = new GridPane();
        content.setMaxWidth(Double.MAX_VALUE);
        content.add(textArea, 0, 0);

        alert.getDialogPane().setContent(content);
        alert.getDialogPane().getStyleClass().add("long-message-alert");

        // ENRICHISSEMENT : Ajuster la taille de la fenêtre
        alert.getDialogPane().setPrefSize(600, 400);
        alert.setResizable(true);

        alert.showAndWait();

        logger.info("Long message alert shown: {}", header);
    }

    /**
     * ENRICHISSEMENT : Affiche une alerte de progression (sans boutons)
     */
    public static Alert showProgressAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        // ENRICHISSEMENT : Supprimer tous les boutons
        alert.getButtonTypes().clear();

        // ENRICHISSEMENT : Ajouter un style pour l'indicateur de progression
        alert.getDialogPane().getStyleClass().add("progress-alert");

        // ENRICHISSEMENT : Rendre non-modale pour permettre l'interaction
        alert.initModality(javafx.stage.Modality.NONE);

        alert.show();
        logger.info("Progress alert shown: {}", header);

        return alert;
    }

    /**
     * ENRICHISSEMENT : Ferme une alerte de progression
     */
    public static void closeProgressAlert(Alert progressAlert) {
        if (progressAlert != null && progressAlert.isShowing()) {
            progressAlert.close();
            logger.info("Progress alert closed");
        }
    }

    /**
     * ENRICHISSEMENT : Met à jour le contenu d'une alerte de progression
     */
    public static void updateProgressAlert(Alert progressAlert, String newContent) {
        if (progressAlert != null && progressAlert.isShowing()) {
            progressAlert.setContentText(newContent);
            logger.debug("Progress alert updated: {}", newContent);
        }
    }

    // ================= MÉTHODES UTILITAIRES ENRICHIES =================

    /**
     * ENRICHISSEMENT : Définit un style CSS personnalisé pour toutes les alertes
     */
    public static void setGlobalAlertStyle(String cssClass) {
        // Cette méthode peut être utilisée pour appliquer un style global
        logger.info("Global alert style set to: {}", cssClass);
    }

    /**
     * ENRICHISSEMENT : Crée un bouton personnalisé avec texte français
     */
    public static ButtonType createCustomButton(String text, ButtonBar.ButtonData buttonData) {
        return new ButtonType(text, buttonData);
    }

    /**
     * ENRICHISSEMENT : Boutons prédéfinis en français
     */
    public static class FrenchButtons {
        public static final ButtonType OUI = new ButtonType("Oui", ButtonBar.ButtonData.YES);
        public static final ButtonType NON = new ButtonType("Non", ButtonBar.ButtonData.NO);
        public static final ButtonType OK = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        public static final ButtonType ANNULER = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        public static final ButtonType APPLIQUER = new ButtonType("Appliquer", ButtonBar.ButtonData.APPLY);
        public static final ButtonType IGNORER = new ButtonType("Ignorer", ButtonBar.ButtonData.OTHER);
        public static final ButtonType REESSAYER = new ButtonType("Réessayer", ButtonBar.ButtonData.OTHER);
    }

    /**
     * ENRICHISSEMENT : Méthode de convenance pour afficher une erreur de validation
     */
    public static void showValidationError(String fieldName, String errorMessage) {
        showError("Erreur de validation",
                String.format("Champ '%s': %s", fieldName, errorMessage));
    }

    /**
     * ENRICHISSEMENT : Méthode de convenance pour afficher un succès d'opération
     */
    public static void showOperationSuccess(String operation) {
        showSuccess("Opération réussie",
                String.format("L'opération '%s' s'est terminée avec succès.", operation));
    }

    /**
     * ENRICHISSEMENT : Méthode de convenance pour confirmer une suppression
     */
    public static boolean confirmDeletion(String itemName) {
        return showConfirmation("Confirmer la suppression",
                String.format("Êtes-vous sûr de vouloir supprimer '%s' ?\n\nCette action est irréversible.", itemName));
    }

    /**
     * ENRICHISSEMENT : Méthode de convenance pour confirmer une modification
     */
    public static boolean confirmModification(String itemName) {
        return showConfirmation("Confirmer la modification",
                String.format("Êtes-vous sûr de vouloir modifier '%s' ?", itemName));
    }

    /**
     * ENRICHISSEMENT : Alerte d'information rapide sans header
     */
    public static void quickInfo(String message) {
        showInfo("Information", message);
    }

    /**
     * ENRICHISSEMENT : Alerte d'erreur rapide sans header
     */
    public static void quickError(String message) {
        showError("Erreur", message);
    }

    /**
     * ENRICHISSEMENT : Alerte d'avertissement rapide sans header
     */
    public static void quickWarning(String message) {
        showWarning("Attention", message);
    }
}