package com.ecommerce.product.mapper;

import com.ecommerce.product.dto.request.ProductCreateRequest;
import com.ecommerce.product.dto.request.ProductUpdateRequest;
import com.ecommerce.product.dto.response.ProductDetailResponse;
import com.ecommerce.product.dto.response.ProductSummaryResponse;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.enums.StockStatus;
import com.ecommerce.product.mapper.product.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMapperTest {

    private ProductMapper productMapper;

    @BeforeEach
    void setUp() {
        productMapper = new ProductMapper();
    }

    @Test
    @DisplayName("Should map ProductCreateRequest to Product entity for creation")
    void testToEntity() {
        Category category = Category.builder().id(10L).name("Books").slug("books").build();
        ProductCreateRequest request = ProductCreateRequest.builder()
                .sku("  book-algo-01  ")
                .name("  Introduction to Algorithms  ")
                .description("CLRS Book")
                .price(new BigDecimal("89.99"))
                .stockQuantity(25L)
                .lowStockThreshold(5L)
                .categoryId(10L)
                .build();

        Product entity = productMapper.toEntity(request, category, 2L, StockStatus.IN_STOCK);

        assertThat(entity).isNotNull();
        assertThat(entity.getSku()).isEqualTo("BOOK-ALGO-01");
        assertThat(entity.getName()).isEqualTo("Introduction to Algorithms");
        assertThat(entity.getDescription()).isEqualTo("CLRS Book");
        assertThat(entity.getPrice()).isEqualTo(new BigDecimal("89.99"));
        assertThat(entity.getStockQuantity()).isEqualTo(25L);
        assertThat(entity.getLowStockThreshold()).isEqualTo(5L);
        assertThat(entity.getStockStatus()).isEqualTo(StockStatus.IN_STOCK);
        assertThat(entity.getSellerId()).isEqualTo(2L);
        assertThat(entity.getCategory()).isEqualTo(category);
        assertThat(entity.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("Should update existing Product entity with update request and new category/stockStatus")
    void testUpdateEntity() {
        Category oldCategory = Category.builder().id(1L).name("Old").build();
        Category newCategory = Category.builder().id(2L).name("New").build();

        Product product = Product.builder()
                .id(100L)
                .name("Old Name")
                .description("Old Desc")
                .price(new BigDecimal("10.00"))
                .stockQuantity(20L)
                .lowStockThreshold(10L)
                .stockStatus(StockStatus.IN_STOCK)
                .category(oldCategory)
                .isActive(true)
                .build();

        ProductUpdateRequest request = ProductUpdateRequest.builder()
                .name("  Updated Name  ")
                .price(new BigDecimal("15.00"))
                .stockQuantity(3L)
                .isActive(false)
                .build();

        productMapper.updateEntity(product, request, newCategory, StockStatus.LOW_STOCK);

        assertThat(product.getName()).isEqualTo("Updated Name");
        assertThat(product.getDescription()).isEqualTo("Old Desc"); // Unchanged
        assertThat(product.getPrice()).isEqualTo(new BigDecimal("15.00"));
        assertThat(product.getStockQuantity()).isEqualTo(3L);
        assertThat(product.getStockStatus()).isEqualTo(StockStatus.LOW_STOCK);
        assertThat(product.getCategory()).isEqualTo(newCategory);
        assertThat(product.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("Should map Product entity to ProductSummaryResponse without sensitive stock counts")
    void testToSummaryResponse() {
        Category category = Category.builder().id(3L).name("Laptops").slug("laptops").build();
        Product product = Product.builder()
                .id(1L)
                .sku("LAPTOP-01")
                .name("Gaming Laptop")
                .price(new BigDecimal("1200.00"))
                .stockQuantity(15L)
                .stockStatus(StockStatus.IN_STOCK)
                .sellerId(10L)
                .category(category)
                .build();

        ProductSummaryResponse summary = productMapper.toSummaryResponse(product);

        assertThat(summary).isNotNull();
        assertThat(summary.getId()).isEqualTo(1L);
        assertThat(summary.getSku()).isEqualTo("LAPTOP-01");
        assertThat(summary.getName()).isEqualTo("Gaming Laptop");
        assertThat(summary.getPrice()).isEqualTo(new BigDecimal("1200.00"));
        assertThat(summary.getStockStatus()).isEqualTo(StockStatus.IN_STOCK);
        assertThat(summary.getCategoryId()).isEqualTo(3L);
        assertThat(summary.getCategoryName()).isEqualTo("Laptops");
        assertThat(summary.getCategorySlug()).isEqualTo("laptops");
        assertThat(summary.getSellerId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Should map Product entity to ProductDetailResponse with urgency message when LOW_STOCK")
    void testToDetailResponseLowStock() {
        Category category = Category.builder().id(3L).name("Laptops").slug("laptops").build();
        Product product = Product.builder()
                .id(1L)
                .sku("LAPTOP-01")
                .name("Gaming Laptop")
                .price(new BigDecimal("1200.00"))
                .stockQuantity(3L)
                .lowStockThreshold(5L)
                .stockStatus(StockStatus.LOW_STOCK)
                .sellerId(10L)
                .category(category)
                .isActive(true)
                .build();

        ProductDetailResponse detail = productMapper.toDetailResponse(product);

        assertThat(detail).isNotNull();
        assertThat(detail.getStockStatus()).isEqualTo(StockStatus.LOW_STOCK);
        assertThat(detail.getDisplayStockCount()).isEqualTo(3L);
        assertThat(detail.getUrgencyMessage()).isEqualTo("Only 3 left in stock - order soon!");
        assertThat(detail.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("Should hide display count in ProductDetailResponse when IN_STOCK")
    void testToDetailResponseInStock() {
        Product product = Product.builder()
                .id(1L)
                .sku("LAPTOP-01")
                .name("Gaming Laptop")
                .price(new BigDecimal("1200.00"))
                .stockQuantity(50L)
                .lowStockThreshold(5L)
                .stockStatus(StockStatus.IN_STOCK)
                .sellerId(10L)
                .isActive(true)
                .build();

        ProductDetailResponse detail = productMapper.toDetailResponse(product);

        assertThat(detail).isNotNull();
        assertThat(detail.getStockStatus()).isEqualTo(StockStatus.IN_STOCK);
        assertThat(detail.getDisplayStockCount()).isNull();
        assertThat(detail.getUrgencyMessage()).isNull();
    }
}
