package com.ling.linginnerflow.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for all domain-level exceptions.
 * Carries an HTTP status and a short error code for machine-readable responses.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public BusinessException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
}
