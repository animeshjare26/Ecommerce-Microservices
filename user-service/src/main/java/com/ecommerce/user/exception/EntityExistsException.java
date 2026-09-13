package com.ecommerce.user.exception;

/**
 * =====================================================================================
 * FILE: EntityExistsException.java
 * MODULE: user-service
 * PURPOSE: Thrown when attempting to register an already existing unique entity (e.g. Email).
 * =====================================================================================
 */
public class EntityExistsException extends RuntimeException {

    public EntityExistsException(String message) {
        super(message);
    }
}
