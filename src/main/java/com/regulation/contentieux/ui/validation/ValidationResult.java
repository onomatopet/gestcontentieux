package com.regulation.contentieux.ui.validation;

import javafx.scene.control.Control;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Résultat d'une validation de formulaire
 */
public class ValidationResult {

    private final Map<Control, List<String>> errors = new HashMap<>();

    /**
     * Ajoute une erreur
     */
    public void addError(Control field, String message) {
        errors.computeIfAbsent(field, k -> new ArrayList<>()).add(message);
    }

    /**
     * Vérifie si la validation est réussie
     */
    public boolean isValid() {
        return errors.isEmpty();
    }

    /**
     * Obtient toutes les erreurs
     */
    public Map<Control, List<String>> getErrors() {
        return new HashMap<>(errors);
    }

    /**
     * Obtient les erreurs d'un champ spécifique
     */
    public List<String> getFieldErrors(Control field) {
        return errors.getOrDefault(field, new ArrayList<>());
    }

    /**
     * Obtient le nombre total d'erreurs
     */
    public int getErrorCount() {
        return errors.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Obtient un résumé des erreurs
     */
    public String getErrorSummary() {
        if (isValid()) {
            return "Aucune erreur";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("Erreurs de validation (").append(getErrorCount()).append(") :\n");

        for (Map.Entry<Control, List<String>> entry : errors.entrySet()) {
            for (String error : entry.getValue()) {
                summary.append("• ").append(error).append("\n");
            }
        }

        return summary.toString();
    }
}