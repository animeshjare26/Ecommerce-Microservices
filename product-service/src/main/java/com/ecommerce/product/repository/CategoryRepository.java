package com.ecommerce.product.repository;

import com.ecommerce.product.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * =====================================================================================
 * FILE: product-service/.../repository/CategoryRepository.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: Spring Data JPA Repository for Category domain entities.
 *
 * DESIGN PATTERN / ARCHITECTURAL CONCEPT:
 * - Repository Pattern: Mediates between the domain and data mapping layers.
 *
 * READING ORDER:
 * - Read PREVIOUS: entity/Category.java
 * - Read THIS FILE: Understand Category query methods.
 * - Read NEXT: repository/ProductRepository.java
 *
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q: How does Spring Data JPA generate queries from method names like `findBySlug`?
 * A: During application startup, Spring Data's `PartTree` parses the method name tokens
 *    (`findBy`, `Slug`), introspects the entity metadata via reflection, and builds an
 *    Abstract Syntax Tree (AST) which is converted into a native SQL PreparedStatement.
 * =====================================================================================
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Finds a category by its unique URL-friendly slug.
     */
    Optional<Category> findBySlug(String slug);

    /**
     * Finds all currently active categories for public browsing.
     */
    List<Category> findAllByIsActiveTrue();

    /**
     * Checks if a category slug already exists (used during category creation).
     */
    boolean existsBySlug(String slug);

    /**
     * Checks if a category name already exists (case-insensitive).
     */
    boolean existsByNameIgnoreCase(String name);
}
