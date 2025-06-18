package com.regulation.contentieux.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Utilitaire pour afficher des alertes et dialogues
 */
public class AlertUtil {

    private static final Logger logger = LoggerFactory.getLogger(AlertUtil.class);

    /**
     * Affiche une alerte d'information
     */
    public static void showInfoAlert(String title, String header, String content) {
        showAlert(Alert.AlertType.INFORMATION, title, header, content);
    }

    /**
     * Affiche une alerte d'erreur
     */
    public static void showErrorAlert(String title, String header, String content) {
        showAlert(Alert.AlertType.ERROR, title, header, content);
        logger.error("Alerte d'erreur affichée - {}: {}", header, content);
    }

    /**
     * Affiche une alerte d'avertissement
     */
    public static void showWarningAlert(String title, String header, String content) {
        showAlert(Alert.AlertType.WARNING, title, header, content);
        logger.warn("Alerte d'avertissement affichée - {}: {}", header, content);
    }

    /**
     * Affiche une alerte de confirmation et retourne le résultat
     */
    public static boolean showConfirmAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        // Configuration des boutons
        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

        Optional<ButtonType> result = alert.showAndWait();
        boolean confirmed = result.isPresent() && result.get() == ButtonType.YES;

        logger.debug("Confirmation '{}': {}", header, confirmed ? "Acceptée" : "Refusée");
        return confirmed;
    }

    /**
     * Affiche une alerte de confirmation avec des boutons personnalisés
     */
    public static Optional<ButtonType> showConfirmAlert(String title, String header, String content,
                                                        ButtonType... buttonTypes) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        if (buttonTypes.length > 0) {
            alert.getButtonTypes().setAll(buttonTypes);
        }

        return alert.showAndWait();
    }

    /**
     * Affiche un dialogue de saisie de texte
     */
    public static Optional<String> showTextInputDialog(String title, String header,
                                                       String content, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(content);

        return dialog.showAndWait();
    }

    /**
     * Affiche un dialogue de saisie de texte simple
     */
    public static Optional<String> showTextInputDialog(String title, String content) {
        return showTextInputDialog(title, null, content, "");
    }

    /**
     * Affiche une alerte avec exception détaillée
     */
    public static void showExceptionAlert(String title, String header, Exception exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText("Une erreur technique s'est produite.");

        // Ajout des détails de l'exception
        javafx.scene.control.TextArea textArea = new javafx.scene.control.TextArea();
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);

        // Construction du message d'erreur détaillé
        StringBuilder details = new StringBuilder();
        details.append("Exception: ").append(exception.getClass().getSimpleName()).append("\n");
        details.append("Message: ").append(exception.getMessage()).append("\n\n");
        details.append("Stack trace:\n");

        for (StackTraceElement element : exception.getStackTrace()) {
            details.append(element.toString()).append("\n");
        }

        textArea.setText(details.toString());

        // Ajout de la zone de texte à l'alerte
        alert.getDialogPane().setExpandableContent(textArea);
        alert.getDialogPane().setExpanded(false);

        alert.showAndWait();

        logger.error("Exception affichée à l'utilisateur", exception);
    }

    /**
     * Affiche une alerte de succès
     */
    public static void showSuccessAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        // Style personnalisé pour le succès
        alert.getDialogPane().getStyleClass().add("success-alert");

        alert.showAndWait();
        logger.info("Succès: {}", content);
    }

    /**
     * Affiche une alerte de progression (non-bloquante)
     */
    public static Alert showProgressAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        // Supprime les boutons pour rendre l'alerte non-interactive
        alert.getButtonTypes().clear();

        // Affichage non-bloquant
        alert.show();

        return alert;
    }

    /**
     * Méthode générique pour afficher une alerte
     */
    private static void showAlert(Alert.AlertType alertType, String title, String header, String content) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        // Configuration de la fenêtre
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.setAlwaysOnTop(false);

        alert.showAndWait();
    }

    /**
     * Affiche une alerte avec possibilité de ne plus afficher
     */
    public static boolean showAlertWithDontShowAgain(String title, String header, String content,
                                                     String preferenceKey) {
        // TODO: Implémenter la gestion des préférences
        // Pour l'instant, affiche toujours l'alerte
        return showConfirmAlert(title, header, content + "\n\n(Option 'Ne plus afficher' sera disponible prochainement)");
    }

    /**
     * Affiche une alerte de validation avec liste d'erreurs
     */
    public static void showValidationAlert(String title, java.util.List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return;
        }

        StringBuilder content = new StringBuilder();
        content.append("Les erreurs suivantes ont été détectées :\n\n");

        for (int i = 0; i < errors.size(); i++) {
            content.append("• ").append(errors.get(i));
            if (i < errors.size() - 1) {
                content.append("\n");
            }
        }

        showErrorAlert(title, "Erreurs de validation", content.toString());
    }

    /**
     * Affiche une alerte avec timeout automatique
     */
    public static void showTimedAlert(String title, String header, String content, int secondes) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        // TODO: Implémenter le timeout automatique
        // Pour l'instant, affiche une alerte normale
        alert.showAndWait();
    }

    /**
     * Utilitaire pour centrer une alerte sur une fenêtre parent
     */
    public static void centerAlertOnStage(Alert alert, Stage parentStage) {
        if (parentStage != null) {
            alert.initOwner(parentStage);
        }
    }
}