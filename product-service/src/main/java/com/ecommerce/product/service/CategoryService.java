package com.ecommerce.product.service;

import com.ecommerce.product.dto.request.CategoryRequest;
import com.ecommerce.product.dto.response.CategoryResponse;

import java.util.List;

/**
 * =====================================================================================
 * FILE: product-service/.../service/CategoryService.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: Service contract for category operations.
 * =====================================================================================
 */
public interface CategoryService {

    List<CategoryResponse> getAllActiveCategories();

    CategoryResponse getCategoryById(Long id);

    CategoryResponse getCategoryBySlug(String slug);

    CategoryResponse createCategory(CategoryRequest request);

    CategoryResponse updateCategory(Long id, CategoryRequest request);

    void deleteCategory(Long id);
}
