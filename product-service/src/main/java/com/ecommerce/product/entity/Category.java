package com.ecommerce.product.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * =====================================================================================
 * FILE: product-service/.../entity/Category.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 *
 * WHAT IS THIS ENTITY AND KEY DESIGN DECISIONS:
 * -------------------------------------------------------------------------------------
 * Represents a product category (e.g., Electronics, Fashion, Books).
 *
 * KEY ARCHITECTURAL CHOICES:
 * 1. `@Getter` AND `@Setter` INSTEAD OF `@Data`:
 *    We avoid Lombok's `@Data` on JPA entities because it auto-generates `equals()`,
 *    `hashCode()`, and `toString()`. When entities have relationships, `@Data` can cause
 *    infinite recursion and a fatal `StackOverflowError`.
 *
 * 2. SOFT DELETION (`is_active`):
 *    Deactivating a category sets `isActive = false` rather than deleting rows, preventing
 *    foreign key integrity violations with existing products.
 *
 * 3. AUDITING (`BaseAuditEntity`):
 *    Inherits audit fields (`createdAt`, `updatedAt`, `createdBy`, `updatedBy`) from
 *    `BaseAuditEntity` for consistent enterprise tracing.
 *
 * READING ORDER:
 * - Read PREVIOUS: enums/StockStatus.java
 * - Read THIS FILE: Understand Category properties and relationships.
 * - Read NEXT: entity/Product.java
 *
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q: Why should you NEVER use `@Data` on JPA Entities with bidirectional relationships?
 * A: Lombok's `@Data` automatically generates `equals()`, `hashCode()`, and `toString()`.
 *    In bidirectional relationships (Category <-> Product), `toString()` and `hashCode()`
 *    will call each other recursively, resulting in a fatal `StackOverflowError`!
 *    Instead, use `@Getter` and `@Setter` with explicit `equals()`/`hashCode()` based on business keys (slug).
 * =====================================================================================
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private List<Product> products = new ArrayList<>();

}
