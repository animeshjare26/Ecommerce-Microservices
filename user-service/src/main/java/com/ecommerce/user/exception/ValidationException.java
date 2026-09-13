package com.ecommerce.user.exception;

/**
 * =====================================================================================
 * FILE: ValidationException.java
 * MODULE: user-service
 * PURPOSE: Thrown when business validation fails (e.g., token expired, passwords do not match).
 * =====================================================================================
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
