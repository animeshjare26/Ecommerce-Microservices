package com.ecommerce.product.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * =====================================================================================
 * FILE: product-service/.../dto/request/ProductUpdateRequest.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: Validated payload for updating existing product information.
 * =====================================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductUpdateRequest {

    @Size(max = 255, message = "Product name cannot exceed 255 characters")
    private String name;

    private String description;

    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    private BigDecimal price;

    @Min(value = 0, message = "Stock quantity cannot be negative")
    private Long stockQuantity;

    @Min(value = 1, message = "Low stock threshold must be at least 1")
    private Long lowStockThreshold;

    private Long categoryId;

    private Boolean isActive;
}
