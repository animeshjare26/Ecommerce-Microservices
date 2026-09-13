package com.ecommerce.product.exception;

/**
 * =====================================================================================
 * FILE: product-service/.../exception/DuplicateResourceException.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: Unchecked exception thrown when a unique constraint (e.g. SKU, slug) is violated.
 * =====================================================================================
 */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
