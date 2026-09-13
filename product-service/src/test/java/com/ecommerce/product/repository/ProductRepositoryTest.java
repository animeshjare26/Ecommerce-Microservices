package com.ecommerce.product.repository;

import com.ecommerce.product.entity.Category;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.enums.StockStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * =====================================================================================
 * FILE: product-service/.../repository/ProductRepositoryTest.java
 * PURPOSE: Slice test verifying ProductRepository JPA methods, @EntityGraph, and search filters.
 * =====================================================================================
 */
import com.ecommerce.product.config.AuditorAwareImpl;
import com.ecommerce.product.config.JpaAuditingConfig;
import org.springframework.context.annotation.Import;

@DataJpaTest
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category testCategory;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        testCategory = categoryRepository.save(Category.builder()
                .name("Electronics")
                .slug("electronics")
                .description("Smartphones and laptops")
                .isActive(true)
                .build());

        testProduct = productRepository.save(Product.builder()
                .sku("TEST-SKU-001")
                .name("Wireless Noise Canceling Headphones")
                .description("High-fidelity sound with 30-hour battery life")
                .price(new BigDecimal("299.99"))
                .stockQuantity(15L)
                .lowStockThreshold(5L)
                .stockStatus(StockStatus.IN_STOCK)
                .sellerId(1L)
                .category(testCategory)
                .isActive(true)
                .build());
    }

    @Test
    @DisplayName("Should find product by SKU and eager-load category via @EntityGraph")
    void testFindBySkuAndIsActiveTrue() {
        Optional<Product> found = productRepository.findBySkuAndIsActiveTrue("TEST-SKU-001");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Wireless Noise Canceling Headphones");
        assertThat(found.get().getCategory()).isNotNull();
        assertThat(found.get().getCategory().getName()).isEqualTo("Electronics");
    }

    @Test
    @DisplayName("Should retrieve paginated products by category slug")
    void testFindByCategorySlug() {
        Page<Product> page = productRepository.findByCategorySlugAndIsActiveTrue("electronics", PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getSku()).isEqualTo("TEST-SKU-001");
    }

    @Test
    @DisplayName("Should search products by keyword and price range")
    void testSearchProducts() {
        Page<Product> page = productRepository.searchProducts(
                "Headphones",
                new BigDecimal("200.00"),
                new BigDecimal("400.00"),
                PageRequest.of(0, 10)
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getSku()).isEqualTo("TEST-SKU-001");
    }

    @Test
    @DisplayName("Should return empty search result when price is outside range")
    void testSearchProductsOutsidePriceRange() {
        Page<Product> page = productRepository.searchProducts(
                "Headphones",
                new BigDecimal("500.00"),
                new BigDecimal("1000.00"),
                PageRequest.of(0, 10)
        );

        assertThat(page.getTotalElements()).isZero();
    }
}
