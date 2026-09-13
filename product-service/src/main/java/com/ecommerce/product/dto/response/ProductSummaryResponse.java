package com.ecommerce.product.dto.response;

import com.ecommerce.product.enums.StockStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * =====================================================================================
 * FILE: product-service/.../dto/response/ProductSummaryResponse.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: Lightweight projection DTO returned for catalog search and category browsing.
 *
 * DESIGN PATTERN / ARCHITECTURAL CONCEPT:
 * - Data Transfer Object (DTO) Pattern: Decouples the internal database schema from the public API.
 * - Event Dampening Principle: Intentionally exposes `StockStatus` (IN_STOCK, LOW_STOCK, OUT_OF_STOCK)
 *   without raw fluctuating integers, preventing list view re-render thrashing and maximizing Redis cache hits.
 * =====================================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSummaryResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String sku;
    private String name;
    private BigDecimal price;
    private StockStatus stockStatus;
    private Long categoryId;
    private String categoryName;
    private String categorySlug;
    private Long sellerId;
}
