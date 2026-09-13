package com.ecommerce.product.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * =====================================================================================
 * FILE: product-service/.../dto/request/ProductCreateRequest.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: Validated payload for creating a new product listing.
 * =====================================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCreateRequest {

    @NotBlank(message = "SKU must not be blank")
    @Size(max = 100, message = "SKU cannot exceed 100 characters")
    private String sku;

    @NotBlank(message = "Product name must not be blank")
    @Size(max = 255, message = "Product name cannot exceed 255 characters")
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    private BigDecimal price;

    @NotNull(message = "Stock quantity is required")
    @Min(value = 0, message = "Stock quantity cannot be negative")
    @Builder.Default
    private Long stockQuantity = 0L;

    @Min(value = 1, message = "Low stock threshold must be at least 1")
    @Builder.Default
    private Long lowStockThreshold = 10L;

    @NotNull(message = "Category ID is required")
    private Long categoryId;
}
