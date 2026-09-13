package com.ecommerce.product.dto.response;

import com.ecommerce.product.enums.StockStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * =====================================================================================
 * FILE: product-service/.../dto/response/ProductDetailResponse.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: Comprehensive DTO returned when a user views a single Product Details Page (PDP).
 *
 * DESIGN PATTERN / ARCHITECTURAL CONCEPT:
 * - Conditional Presentation Pattern:
 *   If stock is abundant (`IN_STOCK`), raw quantities are hidden.
 *   If stock drops below threshold (`LOW_STOCK`), `displayStockCount` and `urgencyMessage`
 *   are surfaced to drive authentic conversion urgency.
 * =====================================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductDetailResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String sku;
    private String name;
    private String description;
    private BigDecimal price;
    private StockStatus stockStatus;

    /**
     * Surfaced ONLY when stockStatus == LOW_STOCK (e.g. 4 units left).
     */
    private Long displayStockCount;

    /**
     * Dynamic urgency callout (e.g., "Only 4 left in stock - order soon!").
     */
    private String urgencyMessage;

    private Long lowStockThreshold;
    private Long categoryId;
    private String categoryName;
    private String categorySlug;
    private Long sellerId;
    private Boolean isActive;
    private Long createdBy;
    private Long updatedBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
