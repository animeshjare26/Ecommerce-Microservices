package com.ecommerce.product.service.impl;

import com.ecommerce.product.config.RedisConfig;
import com.ecommerce.product.dto.request.ProductCreateRequest;
import com.ecommerce.product.dto.request.ProductUpdateRequest;
import com.ecommerce.product.dto.response.PageResponse;
import com.ecommerce.product.dto.response.ProductDetailResponse;
import com.ecommerce.product.dto.response.ProductSummaryResponse;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.enums.StockStatus;
import com.ecommerce.product.exception.DuplicateResourceException;
import com.ecommerce.product.exception.ResourceNotFoundException;
import com.ecommerce.product.exception.UnauthorizedActionException;
import com.ecommerce.product.mapper.product.ProductMapper;
import com.ecommerce.product.repository.CategoryRepository;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * =====================================================================================
 * FILE: product-service/.../service/impl/ProductServiceImpl.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: Implements product catalog business operations, caching, and threshold updates.
 *
 * DESIGN PATTERN / ARCHITECTURAL CONCEPT:
 * - Read-Through Cache Pattern (`@Cacheable`): Product detail requests hit Redis first.
 *   On cache miss, loads from PostgreSQL via `@EntityGraph` (1 query) and populates Redis.
 * - Cache Invalidation Pattern (`@CacheEvict`): Ensures stale catalog data is purged on mutations.
 * - Event Dampening Principle: Separates summary projections (without fluctuating counts)
 *   from detailed projections (which include urgency messages only when in LOW_STOCK).
 *
 * READING ORDER:
 * - Read PREVIOUS: service/ProductService.java
 * - Read THIS FILE: Understand transactional methods, cache annotations, and threshold logic.
 * - Read NEXT: controller/ProductController.java
 * =====================================================================================
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> getAllProducts(Pageable pageable) {
        log.debug("Fetching paginated active products [page: {}, size: {}]", pageable.getPageNumber(), pageable.getPageSize());
        Page<Product> productPage = productRepository.findAllByIsActiveTrue(pageable);
        return PageResponse.from(productPage.map(productMapper::toSummaryResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> getProductsByCategory(String categorySlug, Pageable pageable) {
        log.debug("Fetching products for category slug [{}]", categorySlug);
        if (!categoryRepository.existsBySlug(categorySlug)) {
            throw new ResourceNotFoundException("Category not found with slug: " + categorySlug);
        }
        Page<Product> productPage = productRepository.findByCategorySlugAndIsActiveTrue(categorySlug, pageable);
        return PageResponse.from(productPage.map(productMapper::toSummaryResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> searchProducts(String keyword, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        log.debug("Searching products [keyword: {}, minPrice: {}, maxPrice: {}]", keyword, minPrice, maxPrice);
        Page<Product> productPage = productRepository.searchProducts(keyword, minPrice, maxPrice, pageable);
        return PageResponse.from(productPage.map(productMapper::toSummaryResponse));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = RedisConfig.CACHE_PRODUCTS, key = "#id")
    public ProductDetailResponse getProductById(Long id) {
        log.debug("Database Cache-Miss: Fetching product by id [{}]", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));

        if (!Boolean.TRUE.equals(product.getIsActive())) {
            throw new ResourceNotFoundException("Product is no longer active: " + id);
        }

        return productMapper.toDetailResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = RedisConfig.CACHE_PRODUCTS, key = "#sku")
    public ProductDetailResponse getProductBySku(String sku) {
        log.debug("Database Cache-Miss: Fetching product by sku [{}]", sku);
        Product product = productRepository.findBySkuAndIsActiveTrue(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with SKU: " + sku));
        return productMapper.toDetailResponse(product);
    }

    @Override
    @Transactional
    public ProductDetailResponse createProduct(ProductCreateRequest request, Long sellerId) {
        log.info("Creating product with SKU [{}] for seller [{}]", request.getSku(), sellerId);

        if (productRepository.existsBySku(request.getSku())) {
            throw new DuplicateResourceException("Product with SKU '" + request.getSku() + "' already exists");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));

        Long stockQuantity = request.getStockQuantity() != null ? request.getStockQuantity() : 0L;
        Long lowStockThreshold = request.getLowStockThreshold() != null ? request.getLowStockThreshold() : 10L;
        StockStatus stockStatus = calculateStockStatus(stockQuantity, lowStockThreshold);

        Product product = productMapper.toEntity(request, category, sellerId, stockStatus);
        Product saved = productRepository.save(product);
        return productMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = RedisConfig.CACHE_PRODUCTS, key = "#id"),
            @CacheEvict(value = RedisConfig.CACHE_PRODUCTS, key = "#result.sku")
    })
    public ProductDetailResponse updateProduct(Long id, ProductUpdateRequest request, Long currentUserId, boolean isAdmin) {
        log.info("Updating product [{}] by user [{}] (isAdmin: {})", id, currentUserId, isAdmin);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));

        // Enforce Seller ownership verification
        if (!isAdmin && (currentUserId == null || !product.getSellerId().equals(currentUserId))) {
            throw new UnauthorizedActionException("Access denied: You are only allowed to update your own products");
        }

        Category newCategory = null;
        if (request.getCategoryId() != null) {
            newCategory = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));
        }

        StockStatus newStockStatus = null;
        if (request.getStockQuantity() != null || request.getLowStockThreshold() != null) {
            Long qty = request.getStockQuantity() != null ? request.getStockQuantity() : product.getStockQuantity();
            Long threshold = request.getLowStockThreshold() != null ? request.getLowStockThreshold() : product.getLowStockThreshold();
            newStockStatus = calculateStockStatus(qty, threshold);
        }

        productMapper.updateEntity(product, request, newCategory, newStockStatus);

        return productMapper.toDetailResponse(product);
    }

    @Override
    @Transactional
    @CacheEvict(value = RedisConfig.CACHE_PRODUCTS, key = "#id")
    public void deleteProduct(Long id, Long currentUserId, boolean isAdmin) {
        log.info("Soft-deleting product [{}] by user [{}] (isAdmin: {})", id, currentUserId, isAdmin);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));

        // Enforce Seller ownership verification
        if (!isAdmin && (currentUserId == null || !product.getSellerId().equals(currentUserId))) {
            throw new UnauthorizedActionException("Access denied: You are only allowed to delete your own products");
        }

        // Soft delete
        product.setIsActive(false);
    }

    @Override
    @Transactional
    public void updateStockThreshold(String sku, Long newQuantity, StockStatus status) {
        log.info("Updating stock threshold for SKU [{}] to quantity [{}] and status [{}]", sku, newQuantity, status);
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with SKU: " + sku));

        if (newQuantity != null) {
            product.setStockQuantity(newQuantity);
        }

        if (status != null) {
            product.setStockStatus(status);
        } else if (newQuantity != null) {
            product.setStockStatus(calculateStockStatus(product.getStockQuantity(), product.getLowStockThreshold()));
        }
    }

    /**
     * Calculates the stockStatus based on stockQuantity and lowStockThreshold.
     * Keeps business calculation logic cleanly inside the service implementation.
     */
    private StockStatus calculateStockStatus(Long stockQuantity, Long lowStockThreshold) {
        if (stockQuantity == null || stockQuantity <= 0L) {
            return StockStatus.OUT_OF_STOCK;
        } else if (lowStockThreshold != null && stockQuantity <= lowStockThreshold) {
            return StockStatus.LOW_STOCK;
        } else {
            return StockStatus.IN_STOCK;
        }
    }
}
