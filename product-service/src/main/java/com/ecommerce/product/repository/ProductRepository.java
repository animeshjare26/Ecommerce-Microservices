package com.ecommerce.product.repository;

import com.ecommerce.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * =====================================================================================
 * FILE: product-service/.../repository/ProductRepository.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: High-performance Spring Data JPA repository for Product entities.
 *
 * DESIGN PATTERN / ARCHITECTURAL CONCEPT:
 * - Entity Graph Pattern (`@EntityGraph`): Completely eliminates Hibernate's N+1 query problem
 *   by generating an eager SQL `LEFT OUTER JOIN categories` in a single round-trip.
 * - Dynamic Projection & Composite Querying: Enables full-text keyword matching, price range
 *   filtering, and category filtering while leveraging PostgreSQL B-Tree composite indexes.
 *
 * READING ORDER:
 * - Read PREVIOUS: repository/CategoryRepository.java
 * - Read THIS FILE: Understand query execution and @EntityGraph mechanics.
 * - Read NEXT: dto/response/ProductSummaryResponse.java
 *
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: What is the difference between `@EntityGraph` and `JOIN FETCH` in Spring Data JPA?
 * A1: - `JOIN FETCH` forces an `INNER JOIN` in JPQL. If a relationship is null, the parent
 *       entity is dropped from the result set! Also, combining `JOIN FETCH` with paginated
 *       one-to-many collections causes Hibernate to emit `HHH000104: firstResult/maxResults
 *       specified with collection fetch; applying in memory!`, which crashes servers with OOM.
 *     - `@EntityGraph` acts as a fetch plan hint that instructs Hibernate to perform a
 *       `LEFT OUTER JOIN` at SQL generation time. For `@ManyToOne` relationships (Product -> Category),
 *       `@EntityGraph` works cleanly alongside SQL `LIMIT` and `OFFSET` pagination without
 *       in-memory pagination traps.
 *
 * Q2: Why do we pass `Pageable` into the custom `@Query` methods?
 * A2: Spring Data JPA detects `Pageable` as the last parameter, automatically appends
 *     `ORDER BY`, `LIMIT ? OFFSET ?` to the generated SQL, and executes a synchronized
 *     `SELECT COUNT(*)` query to populate the `Page<T>` metadata (total elements, total pages).
 * =====================================================================================
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Retrieves an individual product with its category loaded in a single SQL query.
     * Uses @EntityGraph to eliminate secondary SELECT queries for Category.
     */
    @EntityGraph(attributePaths = {"category"})
    Optional<Product> findById(Long id);

    /**
     * Retrieves an active product by SKU with category loaded.
     */
    @EntityGraph(attributePaths = {"category"})
    Optional<Product> findBySkuAndIsActiveTrue(String sku);

    /**
     * Retrieves any product by SKU (active or inactive) for internal service sync.
     */
    Optional<Product> findBySku(String sku);

    /**
     * Checks if a product with the given SKU already exists.
     */
    boolean existsBySku(String sku);

    /**
     * Retrieves paginated active products for the public catalog browse page.
     * Eliminates N+1 query by eagerly joining category in the same query.
     */
    @EntityGraph(attributePaths = {"category"})
    Page<Product> findAllByIsActiveTrue(Pageable pageable);

    /**
     * Retrieves paginated active products belonging to a specific category slug.
     */
    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT p FROM Product p WHERE p.category.slug = :categorySlug AND p.isActive = true")
    Page<Product> findByCategorySlugAndIsActiveTrue(@Param("categorySlug") String categorySlug, Pageable pageable);

    /**
     * Full catalog search matching name or description, with optional price range filtering.
     */
    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT p FROM Product p WHERE p.isActive = true " +
           "AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:minPrice IS NULL OR p.price >= :minPrice) " +
           "AND (:maxPrice IS NULL OR p.price <= :maxPrice)")
    Page<Product> searchProducts(
            @Param("keyword") String keyword,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable);

    /**
     * Retrieves all products created by a specific seller (for merchant dashboard).
     */
    @EntityGraph(attributePaths = {"category"})
    Page<Product> findBySellerIdAndIsActiveTrue(Long sellerId, Pageable pageable);
}
