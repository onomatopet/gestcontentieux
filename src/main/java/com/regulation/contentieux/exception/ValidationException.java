package com.regulation.contentieux.exception;

/**
 * Exception pour les erreurs de validation
 */
public class ValidationException extends BusinessException {

    private String field;

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}