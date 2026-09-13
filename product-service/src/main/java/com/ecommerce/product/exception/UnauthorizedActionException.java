package com.ecommerce.product.exception;

/**
 * =====================================================================================
 * FILE: product-service/.../exception/UnauthorizedActionException.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: Unchecked exception thrown when a caller lacks required roles (e.g. ROLE_ADMIN).
 * =====================================================================================
 */
public class UnauthorizedActionException extends RuntimeException {
    public UnauthorizedActionException(String message) {
        super(message);
    }
}
