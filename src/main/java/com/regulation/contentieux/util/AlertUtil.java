package com.regulation.contentieux.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Utilitaire pour afficher des alertes JavaFX standardisées
 * Centralise tous les dialogues de l'application
 */
public class AlertUtil {

    // Largeur minimale des alertes
    private static final double MIN_WIDTH = 400;

    /**
     * Affiche une alerte d'information
     */
    public static void showInfoAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        configureAlert(alert, title, header, content);
        alert.showAndWait();
    }

    /**
     * Affiche une alerte d'information avec fenêtre parente
     */
    public static void showInfoAlert(Window owner, String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        configureAlert(alert, title, header, content);
        alert.showAndWait();
    }

    /**
     * Affiche une alerte d'avertissement
     */
    public static void showWarningAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        configureAlert(alert, title, header, content);
        alert.showAndWait();
    }

    /**
     * Affiche une alerte d'avertissement avec fenêtre parente
     */
    public static void showWarningAlert(Window owner, String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(owner);
        configureAlert(alert, title, header, content);
        alert.showAndWait();
    }

    /**
     * Affiche une alerte d'erreur
     */
    public static void showErrorAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        configureAlert(alert, title, header, content);
        alert.showAndWait();
    }

    /**
     * Affiche une alerte d'erreur avec fenêtre parente
     */
    public static void showErrorAlert(Window owner, String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        configureAlert(alert, title, header, content);
        alert.showAndWait();
    }

    /**
     * Affiche une alerte d'erreur avec détails d'exception
     */
    public static void showErrorAlertWithDetails(String title, String header, String content, Exception exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        configureAlert(alert, title, header, content);

        // Créer la zone de texte extensible pour les détails
        String exceptionText = getExceptionText(exception);
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

        // Ajouter le contenu extensible
        alert.getDialogPane().setExpandableContent(expContent);
        alert.showAndWait();
    }

    /**
     * Affiche une alerte de confirmation
     */
    public static boolean showConfirmAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        configureAlert(alert, title, header, content);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * Affiche une alerte de confirmation avec fenêtre parente
     */
    public static boolean showConfirmAlert(Window owner, String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(owner);
        configureAlert(alert, title, header, content);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * Affiche une alerte de confirmation avec boutons personnalisés
     */
    public static Optional<ButtonType> showConfirmAlertWithOptions(String title, String header, String content,
                                                                   ButtonType... buttonTypes) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        configureAlert(alert, title, header, content);

        alert.getButtonTypes().clear();
        alert.getButtonTypes().addAll(buttonTypes);

        return alert.showAndWait();
    }

    /**
     * Affiche un dialogue de saisie de texte
     */
    public static Optional<String> showTextInputDialog(String title, String header, String content, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(content);

        // Configuration de la taille
        dialog.getDialogPane().setMinWidth(MIN_WIDTH);
        dialog.getDialogPane().setPrefWidth(MIN_WIDTH);

        return dialog.showAndWait();
    }

    /**
     * Affiche un dialogue de saisie de texte avec fenêtre parente
     */
    public static Optional<String> showTextInputDialog(Window owner, String title, String header,
                                                       String content, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.initOwner(owner);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(content);

        // Configuration de la taille
        dialog.getDialogPane().setMinWidth(MIN_WIDTH);
        dialog.getDialogPane().setPrefWidth(MIN_WIDTH);

        return dialog.showAndWait();
    }

    /**
     * Affiche une alerte de succès (information avec style)
     */
    public static void showSuccessAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        configureAlert(alert, title, header, content);

        // Ajouter une classe CSS pour le style succès
        alert.getDialogPane().getStyleClass().add("success-alert");

        alert.showAndWait();
    }

    /**
     * Configure une alerte avec les paramètres standards
     */
    private static void configureAlert(Alert alert, String title, String header, String content) {
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        // Configuration de la taille
        alert.getDialogPane().setMinWidth(MIN_WIDTH);
        alert.getDialogPane().setPrefWidth(MIN_WIDTH);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

        // Ajustement automatique de la hauteur
        alert.getDialogPane().setMaxHeight(Region.USE_PREF_SIZE);

        // S'assurer que le contenu est bien affiché
        if (content != null && content.length() > 100) {
            alert.getDialogPane().setPrefWidth(600);
        }
    }

    /**
     * Convertit une exception en texte formaté
     */
    private static String getExceptionText(Exception exception) {
        if (exception == null) {
            return "Aucune information d'exception disponible";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Exception: ").append(exception.getClass().getName()).append("\n");
        sb.append("Message: ").append(exception.getMessage()).append("\n\n");
        sb.append("Stack Trace:\n");

        for (StackTraceElement element : exception.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }

        // Ajouter la cause si elle existe
        Throwable cause = exception.getCause();
        if (cause != null) {
            sb.append("\nCaused by: ").append(cause.getClass().getName());
            sb.append("\nMessage: ").append(cause.getMessage()).append("\n");

            for (StackTraceElement element : cause.getStackTrace()) {
                sb.append("\tat ").append(element.toString()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Affiche une notification temporaire (à implémenter avec une bibliothèque de notifications)
     */
    public static void showNotification(String title, String message) {
        // Pour l'instant, utilise une alerte info
        // TODO: Implémenter avec ControlsFX Notifications ou similaire
        showInfoAlert(title, null, message);
    }

    /**
     * Centre une alerte sur son propriétaire
     */
    public static void centerAlertOnOwner(Alert alert, Window owner) {
        if (owner != null && owner instanceof Stage) {
            Stage ownerStage = (Stage) owner;
            alert.setX(ownerStage.getX() + (ownerStage.getWidth() / 2) - 200);
            alert.setY(ownerStage.getY() + (ownerStage.getHeight() / 2) - 150);
        }
    }
}