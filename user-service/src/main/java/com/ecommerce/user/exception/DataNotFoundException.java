package com.ecommerce.user.exception;

/**
 * =====================================================================================
 * FILE: DataNotFoundException.java
 * MODULE: user-service
 * PURPOSE: Thrown when an expected entity (User, Role) is absent in the database.
 * =====================================================================================
 */
public class DataNotFoundException extends RuntimeException {

    public DataNotFoundException(String message) {
        super(message);
    }
}
