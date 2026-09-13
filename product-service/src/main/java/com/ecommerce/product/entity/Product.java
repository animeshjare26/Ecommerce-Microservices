package com.ecommerce.product.entity;

import com.ecommerce.product.enums.StockStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * =====================================================================================
 * FILE: product-service/.../entity/Product.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: JPA Entity representing an individual product item in the catalog.
 *
 * DESIGN PATTERN / ARCHITECTURAL CONCEPT:
 * - Domain Model Pattern: Encapsulates catalog properties and stock status invariants.
 * - Soft Delete Pattern: `active = false` preserves referential history in past orders.
 * - Event Dampened Inventory Snapshot: `stockStatus`, `stockQuantity`, and `lowStockThreshold`
 *   allow intelligent UI rendering without querying inventory service on every catalog search.
 *
 * READING ORDER:
 * - Read PREVIOUS: entity/Category.java
 * - Read THIS FILE: Understand Product properties, stock threshold logic, and constraints.
 * - Read NEXT: repository/ProductRepository.java
 *
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Why do we use `@ManyToOne(fetch = FetchType.LAZY)` instead of the JPA default `FetchType.EAGER`?
 * A1: In JPA specifications, `@ManyToOne` defaults to `FetchType.EAGER`!
 *     This is an infamous JPA performance trap. If you query 100 products with default EAGER,
 *     Hibernate will execute 1 query for products + 100 separate queries for categories (N+1 problem).
 *     Always explicitly declare `FetchType.LAZY`, and use `@EntityGraph` or `JOIN FETCH` when
 *     the category is genuinely needed.
 *
 * Q2: Why is `stockQuantity` and `lowStockThreshold` typed as `Long` instead of `Integer`?
 * A2: Enterprise inventory systems tracking micro-components, bulk units, or high-volume
 *     global warehouse batches can exceed the 32-bit signed integer limit (2.14 billion).
 *     Using `Long` / `BIGINT` guarantees safety against integer overflow.
 * =====================================================================================
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String sku;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    @Builder.Default
    private Long stockQuantity = 0L;

    @Column(name = "low_stock_threshold", nullable = false)
    @Builder.Default
    private Long lowStockThreshold = 10L;

    @Enumerated(EnumType.STRING)
    @Column(name = "stock_status", nullable = false, length = 30)
    @Builder.Default
    private StockStatus stockStatus = StockStatus.IN_STOCK;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

}
