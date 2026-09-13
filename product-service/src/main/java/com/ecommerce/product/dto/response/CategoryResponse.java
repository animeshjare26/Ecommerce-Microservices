package com.ecommerce.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * =====================================================================================
 * FILE: product-service/.../dto/response/CategoryResponse.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: DTO representing a category in public menus and admin management.
 * =====================================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String slug;
    private String description;
    private Boolean isActive;
    private Long createdBy;
    private Long updatedBy;
    private java.time.OffsetDateTime createdAt;
    private java.time.OffsetDateTime updatedAt;
}
