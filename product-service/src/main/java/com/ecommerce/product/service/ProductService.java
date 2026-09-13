package com.ecommerce.product.service;

import com.ecommerce.product.dto.request.ProductCreateRequest;
import com.ecommerce.product.dto.request.ProductUpdateRequest;
import com.ecommerce.product.dto.response.PageResponse;
import com.ecommerce.product.dto.response.ProductDetailResponse;
import com.ecommerce.product.dto.response.ProductSummaryResponse;
import com.ecommerce.product.enums.StockStatus;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

/**
 * =====================================================================================
 * FILE: product-service/.../service/ProductService.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: Service contract for product catalog operations.
 * =====================================================================================
 */
public interface ProductService {

    PageResponse<ProductSummaryResponse> getAllProducts(Pageable pageable);

    PageResponse<ProductSummaryResponse> getProductsByCategory(String categorySlug, Pageable pageable);

    PageResponse<ProductSummaryResponse> searchProducts(String keyword, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    ProductDetailResponse getProductById(Long id);

    ProductDetailResponse getProductBySku(String sku);

    ProductDetailResponse createProduct(ProductCreateRequest request, Long sellerId);

    ProductDetailResponse updateProduct(Long id, ProductUpdateRequest request, Long currentUserId, boolean isAdmin);

    void deleteProduct(Long id, Long currentUserId, boolean isAdmin);

    void updateStockThreshold(String sku, Long newQuantity, StockStatus status);
}
