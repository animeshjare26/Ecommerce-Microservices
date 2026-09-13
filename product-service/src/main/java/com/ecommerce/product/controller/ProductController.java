package com.ecommerce.product.controller;

import com.ecommerce.product.dto.request.ProductCreateRequest;
import com.ecommerce.product.dto.request.ProductUpdateRequest;
import com.ecommerce.product.dto.response.PageResponse;
import com.ecommerce.product.dto.response.ProductDetailResponse;
import com.ecommerce.product.dto.response.ProductSummaryResponse;
import com.ecommerce.product.enums.StockStatus;
import com.ecommerce.product.security.UserContext;
import com.ecommerce.product.service.ProductService;
import com.ecommerce.product.utils.GenericResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * =====================================================================================
 * FILE: product-service/.../controller/ProductController.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: REST Controller exposing endpoints for product search, catalog browsing, and management.
 *
 * DESIGN PATTERN / ARCHITECTURAL CONCEPT:
 * - Public vs Protected Endpoint Separation:
 *   - Browsing & Search (GET): Publicly accessible, heavily cached in Redis, eliminates N+1 queries.
 *   - Mutations (POST/PUT/DELETE): Authenticated via API Gateway downstream headers (ROLE_ADMIN or ROLE_SELLER).
 * - Event Dampening: Summary responses expose StockStatus; detailed responses show urgency counts.
 * =====================================================================================
 */
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Product Controller", description = "Endpoints for catalog browsing, search, and product management")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Get paginated active products (Public)")
    public ResponseEntity<GenericResponse<PageResponse<ProductSummaryResponse>>> getAllProducts(
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<ProductSummaryResponse> response = productService.getAllProducts(pageable);
        return ResponseEntity.ok(GenericResponse.success(response, "Products retrieved successfully"));
    }

    @GetMapping("/category/{categorySlug}")
    @Operation(summary = "Get products by category slug (Public)")
    public ResponseEntity<GenericResponse<PageResponse<ProductSummaryResponse>>> getProductsByCategory(
            @PathVariable String categorySlug,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<ProductSummaryResponse> response = productService.getProductsByCategory(categorySlug, pageable);
        return ResponseEntity.ok(GenericResponse.success(response, "Products for category retrieved successfully"));
    }

    @GetMapping("/search")
    @Operation(summary = "Search products by keyword and price range (Public)")
    public ResponseEntity<GenericResponse<PageResponse<ProductSummaryResponse>>> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<ProductSummaryResponse> response = productService.searchProducts(keyword, minPrice, maxPrice, pageable);
        return ResponseEntity.ok(GenericResponse.success(response, "Search results retrieved successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get detailed product by ID (Public - Cached)")
    public ResponseEntity<GenericResponse<ProductDetailResponse>> getProductById(@PathVariable Long id) {
        ProductDetailResponse response = productService.getProductById(id);
        return ResponseEntity.ok(GenericResponse.success(response, "Product details retrieved successfully"));
    }

    @GetMapping("/sku/{sku}")
    @Operation(summary = "Get product by SKU (Public/Internal)")
    public ResponseEntity<GenericResponse<ProductDetailResponse>> getProductBySku(@PathVariable String sku) {
        ProductDetailResponse response = productService.getProductBySku(sku);
        return ResponseEntity.ok(GenericResponse.success(response, "Product details retrieved successfully"));
    }

    @PostMapping
    @Operation(summary = "Create a new product listing (Admin or Seller)")
    public ResponseEntity<GenericResponse<ProductDetailResponse>> createProduct(
            @Valid @RequestBody ProductCreateRequest request) {
        Long sellerId = UserContext.getUserId();
        ProductDetailResponse response = productService.createProduct(request, sellerId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GenericResponse.success(response, "Product created successfully"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update product information (Admin or Seller)")
    public ResponseEntity<GenericResponse<ProductDetailResponse>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request) {
        Long currentUserId = UserContext.getUserId();
        UserContext.UserIdentity identity = UserContext.get();
        boolean isAdmin = identity != null && identity.isAdmin();

        ProductDetailResponse response = productService.updateProduct(id, request, currentUserId, isAdmin);
        return ResponseEntity.ok(GenericResponse.success(response, "Product updated successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a product (Admin or Seller)")
    public ResponseEntity<GenericResponse<Void>> deleteProduct(@PathVariable Long id) {
        Long currentUserId = UserContext.getUserId();
        UserContext.UserIdentity identity = UserContext.get();
        boolean isAdmin = identity != null && identity.isAdmin();

        productService.deleteProduct(id, currentUserId, isAdmin);
        return ResponseEntity.ok(GenericResponse.success(null, "Product deleted successfully"));
    }

    @PatchMapping("/sku/{sku}/threshold")
    @Operation(summary = "Update product stock threshold & status (Internal Sync)")
    public ResponseEntity<GenericResponse<Void>> updateStockThreshold(
            @PathVariable String sku,
            @RequestParam(required = false) Long quantity,
            @RequestParam(required = false) StockStatus status) {
        productService.updateStockThreshold(sku, quantity, status);
        return ResponseEntity.ok(GenericResponse.success(null, "Stock threshold updated successfully"));
    }
}
