package com.ecommerce.product.exception;

/**
 * =====================================================================================
 * FILE: product-service/.../exception/ResourceNotFoundException.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: Unchecked exception thrown when an entity is not found in database or cache.
 * =====================================================================================
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
