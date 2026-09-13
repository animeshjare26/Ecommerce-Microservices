package com.ecommerce.product.controller;

import com.ecommerce.product.dto.request.CategoryRequest;
import com.ecommerce.product.dto.response.CategoryResponse;
import com.ecommerce.product.service.CategoryService;
import com.ecommerce.product.utils.GenericResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * =====================================================================================
 * FILE: product-service/.../controller/CategoryController.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: REST Controller exposing endpoints for category management and navigation.
 *
 * DESIGN PATTERN / ARCHITECTURAL CONCEPT:
 * - REST Controller Pattern: Exposes resources via standardized HTTP verbs.
 * - Role-Protected Mutations: State mutations (POST/PUT/DELETE) are guarded by
 *   RoleAuthorizationInterceptor requiring ROLE_ADMIN.
 * =====================================================================================
 */
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Category Controller", description = "Endpoints for managing catalog categories")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Get all active categories (Public)")
    public ResponseEntity<GenericResponse<List<CategoryResponse>>> getAllActiveCategories() {
        List<CategoryResponse> categories = categoryService.getAllActiveCategories();
        return ResponseEntity.ok(GenericResponse.success(categories, "Active categories retrieved successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get category by ID (Public)")
    public ResponseEntity<GenericResponse<CategoryResponse>> getCategoryById(@PathVariable Long id) {
        CategoryResponse category = categoryService.getCategoryById(id);
        return ResponseEntity.ok(GenericResponse.success(category, "Category retrieved successfully"));
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get category by slug (Public)")
    public ResponseEntity<GenericResponse<CategoryResponse>> getCategoryBySlug(@PathVariable String slug) {
        CategoryResponse category = categoryService.getCategoryBySlug(slug);
        return ResponseEntity.ok(GenericResponse.success(category, "Category retrieved successfully"));
    }

    @PostMapping
    @Operation(summary = "Create a new category (Admin Only)")
    public ResponseEntity<GenericResponse<CategoryResponse>> createCategory(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse created = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GenericResponse.success(created, "Category created successfully"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing category (Admin Only)")
    public ResponseEntity<GenericResponse<CategoryResponse>> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {
        CategoryResponse updated = categoryService.updateCategory(id, request);
        return ResponseEntity.ok(GenericResponse.success(updated, "Category updated successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a category (Admin Only)")
    public ResponseEntity<GenericResponse<Void>> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(GenericResponse.success(null, "Category deleted successfully"));
    }
}
