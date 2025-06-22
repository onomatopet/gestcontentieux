package com.regulation.contentieux.exception;

import java.util.ArrayList;
import java.util.List;

/**
 * Exception levée lors d'erreurs de validation
 * Peut contenir plusieurs erreurs
 */
public class ValidationException extends Exception {

    private final List<String> errors;

    /**
     * Constructeur avec un message simple
     */
    public ValidationException(String message) {
        super(message);
        this.errors = new ArrayList<>();
    }

    /**
     * Constructeur avec un message et une cause
     */
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
        this.errors = new ArrayList<>();
    }

    /**
     * Constructeur avec un message et une liste d'erreurs
     */
    public ValidationException(String message, List<String> errors) {
        super(message);
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
    }

    /**
     * Retourne la liste des erreurs
     */
    public List<String> getErrors() {
        return errors;
    }

    /**
     * Vérifie s'il y a plusieurs erreurs
     */
    public boolean hasMultipleErrors() {
        return errors.size() > 1;
    }

    /**
     * Retourne un message formaté avec toutes les erreurs
     */
    public String getFormattedMessage() {
        if (errors.isEmpty()) {
            return getMessage();
        }

        StringBuilder sb = new StringBuilder(getMessage());
        sb.append(" :\n");

        for (int i = 0; i < errors.size(); i++) {
            sb.append("  • ").append(errors.get(i));
            if (i < errors.size() - 1) {
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return getFormattedMessage();
    }
}