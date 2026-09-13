package com.ecommerce.product.mapper.category;

import com.ecommerce.product.dto.request.CategoryRequest;
import com.ecommerce.product.dto.response.CategoryResponse;
import com.ecommerce.product.entity.Category;
import org.springframework.stereotype.Component;

/**
 * =====================================================================================
 * FILE: product-service/.../mapper/category/CategoryMapper.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: Service-wise mapper component for Category domain.
 *          Handles DTO -> Entity (Create / Update) and Entity -> DTO (Get).
 * =====================================================================================
 */
@Component
public class CategoryMapper {

    /**
     * Maps a CategoryRequest DTO to a new Category entity for creation.
     */
    public Category toEntity(CategoryRequest request) {
        if (request == null) {
            return null;
        }

        return Category.builder()
                .name(request.getName() != null ? request.getName().trim() : null)
                .slug(request.getSlug() != null ? request.getSlug().trim().toLowerCase() : null)
                .description(request.getDescription())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();
    }

    /**
     * Updates an existing Category entity from a CategoryRequest DTO.
     */
    public void updateEntity(Category category, CategoryRequest request) {
        if (category == null || request == null) {
            return;
        }

        if (request.getName() != null) {
            category.setName(request.getName().trim());
        }
        if (request.getSlug() != null) {
            category.setSlug(request.getSlug().trim().toLowerCase());
        }
        if (request.getDescription() != null) {
            category.setDescription(request.getDescription());
        }
        if (request.getIsActive() != null) {
            category.setIsActive(request.getIsActive());
        }
    }

    /**
     * Maps a Category JPA entity to a CategoryResponse DTO for read/query operations.
     */
    public CategoryResponse toResponse(Category category) {
        if (category == null) {
            return null;
        }

        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .isActive(category.getIsActive())
                .createdBy(category.getCreatedBy())
                .updatedBy(category.getUpdatedBy())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
