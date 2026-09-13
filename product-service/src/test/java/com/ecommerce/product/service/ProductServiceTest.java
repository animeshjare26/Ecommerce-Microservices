package com.ecommerce.product.service;

import com.ecommerce.product.dto.request.ProductCreateRequest;
import com.ecommerce.product.dto.response.ProductDetailResponse;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.enums.StockStatus;
import com.ecommerce.product.exception.DuplicateResourceException;
import com.ecommerce.product.exception.UnauthorizedActionException;
import com.ecommerce.product.mapper.product.ProductMapper;
import com.ecommerce.product.repository.CategoryRepository;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * =====================================================================================
 * FILE: product-service/.../service/ProductServiceTest.java
 * PURPOSE: Unit tests for ProductService business logic, threshold dampening, and security checks.
 * =====================================================================================
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Spy
    private ProductMapper productMapper = new ProductMapper();

    @InjectMocks
    private ProductServiceImpl productService;

    private Category mockCategory;
    private Product mockProduct;

    @BeforeEach
    void setUp() {
        mockCategory = Category.builder()
                .id(1L)
                .name("Electronics")
                .slug("electronics")
                .isActive(true)
                .build();

        mockProduct = Product.builder()
                .id(100L)
                .sku("LAPTOP-PRO-16")
                .name("High-Performance Laptop 16-inch")
                .description("Powerful workstation")
                .price(new BigDecimal("1999.99"))
                .stockQuantity(4L)
                .lowStockThreshold(10L)
                .stockStatus(StockStatus.LOW_STOCK)
                .sellerId(5L)
                .category(mockCategory)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("Should return low stock urgency message when product is in LOW_STOCK status")
    void testGetProductByIdLowStockUrgency() {
        when(productRepository.findById(100L)).thenReturn(Optional.of(mockProduct));

        ProductDetailResponse response = productService.getProductById(100L);

        assertThat(response).isNotNull();
        assertThat(response.getStockStatus()).isEqualTo(StockStatus.LOW_STOCK);
        assertThat(response.getDisplayStockCount()).isEqualTo(4L);
        assertThat(response.getUrgencyMessage()).isEqualTo("Only 4 left in stock - order soon!");
    }

    @Test
    @DisplayName("Should create product successfully with calculated stock status")
    void testCreateProductSuccess() {
        ProductCreateRequest request = ProductCreateRequest.builder()
                .sku("PHONE-ULTRA-5G")
                .name("Ultra 5G Smartphone")
                .price(new BigDecimal("799.00"))
                .stockQuantity(50L)
                .lowStockThreshold(10L)
                .categoryId(1L)
                .build();

        when(productRepository.existsBySku("PHONE-ULTRA-5G")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(mockCategory));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(200L);
            return p;
        });

        ProductDetailResponse response = productService.createProduct(request, 5L);

        assertThat(response).isNotNull();
        assertThat(response.getSku()).isEqualTo("PHONE-ULTRA-5G");
        assertThat(response.getStockStatus()).isEqualTo(StockStatus.IN_STOCK);
        assertThat(response.getDisplayStockCount()).isNull(); // Hidden when abundant!
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when creating product with existing SKU")
    void testCreateProductDuplicateSku() {
        ProductCreateRequest request = ProductCreateRequest.builder()
                .sku("EXISTING-SKU")
                .name("Product Name")
                .price(new BigDecimal("10.00"))
                .categoryId(1L)
                .build();

        when(productRepository.existsBySku("EXISTING-SKU")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request, 1L))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Should reject seller deleting a product owned by another seller")
    void testDeleteProductUnauthorizedSeller() {
        when(productRepository.findById(100L)).thenReturn(Optional.of(mockProduct));

        // Seller 99 attempts to delete product owned by Seller 5 (isAdmin: false)
        assertThatThrownBy(() -> productService.deleteProduct(100L, 99L, false))
                .isInstanceOf(UnauthorizedActionException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    @DisplayName("Should allow admin to delete any product regardless of seller ID")
    void testDeleteProductByAdminSuccess() {
        when(productRepository.findById(100L)).thenReturn(Optional.of(mockProduct));

        // Admin (isAdmin: true) deletes product owned by Seller 5
        productService.deleteProduct(100L, 99L, true);

        assertThat(mockProduct.getIsActive()).isFalse();
    }
}
