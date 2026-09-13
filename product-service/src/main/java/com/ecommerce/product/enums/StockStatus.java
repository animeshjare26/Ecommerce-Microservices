package com.ecommerce.product.enums;

/**
 * =====================================================================================
 * FILE: product-service/.../enums/StockStatus.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: Defines discrete stock availability categories for catalog display.
 *
 * DESIGN PATTERN / ARCHITECTURAL CONCEPT:
 * - Event Dampening / Threshold Pattern:
 *   Instead of exposing fluctuating integer numbers on high-traffic browse queries,
 *   we expose coarse categorical states (IN_STOCK, LOW_STOCK, OUT_OF_STOCK).
 *   This preserves Redis cache stability and avoids invalidating cache on every decrement.
 *
 * READING ORDER:
 * - Read PREVIOUS: db/migration/V1__init_product_schema.sql
 * - Read THIS FILE: Understand stock states.
 * - Read NEXT: entity/Product.java
 *
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q: Why not compute StockStatus dynamically at runtime rather than storing it?
 * A: Computing it dynamically in SQL (`CASE WHEN stock_quantity > threshold...`)
 *    prevents effective B-Tree indexing on stock status. Storing `stock_status` as a column
 *    allows instantaneous index filtering (`WHERE stock_status = 'IN_STOCK'`) across
 *    millions of products in sub-millisecond query time.
 * =====================================================================================
 */
public enum StockStatus {
    /**
     * Stock is abundant (greater than the low stock threshold).
     */
    IN_STOCK,

    /**
     * Stock has fallen below the configured threshold (e.g. <= 10).
     * Triggers UI urgency messaging ("Only X left in stock!").
     */
    LOW_STOCK,

    /**
     * Zero stock available for ordering.
     */
    OUT_OF_STOCK
}
