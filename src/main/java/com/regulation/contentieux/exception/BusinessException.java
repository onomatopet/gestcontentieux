package com.regulation.contentieux.exception;

/**
 * Exception métier pour les erreurs de logique business
 */
public class BusinessException extends RuntimeException {

    private String code;
    private Object[] params;

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String code, String message, Object... params) {
        super(message);
        this.code = code;
        this.params = params;
    }

    public String getCode() {
        return code;
    }

    public Object[] getParams() {
        return params;
    }
}