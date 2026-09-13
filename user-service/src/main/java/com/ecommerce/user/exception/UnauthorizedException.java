package com.ecommerce.user.exception;

/**
 * =====================================================================================
 * FILE: UnauthorizedException.java
 * MODULE: user-service
 * PURPOSE: Thrown when credentials, tokens, or authorizations are invalid or expired.
 * =====================================================================================
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
