package com.ecommerce.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * =====================================================================================
 * FILE: product-service/.../dto/request/CategoryRequest.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: Validated payload for creating or updating a category.
 * =====================================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRequest {

    @NotBlank(message = "Category name must not be blank")
    @Size(max = 100, message = "Category name cannot exceed 100 characters")
    private String name;

    @NotBlank(message = "Category slug must not be blank")
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Slug must be lowercase alphanumeric with hyphens (e.g., 'home-kitchen')")
    @Size(max = 120, message = "Slug cannot exceed 120 characters")
    private String slug;

    private String description;

    @Builder.Default
    private Boolean isActive = true;
}
