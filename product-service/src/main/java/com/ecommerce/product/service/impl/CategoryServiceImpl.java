package com.ecommerce.product.service.impl;

import com.ecommerce.product.config.RedisConfig;
import com.ecommerce.product.dto.request.CategoryRequest;
import com.ecommerce.product.dto.response.CategoryResponse;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.exception.DuplicateResourceException;
import com.ecommerce.product.exception.ResourceNotFoundException;
import com.ecommerce.product.mapper.category.CategoryMapper;
import com.ecommerce.product.repository.CategoryRepository;
import com.ecommerce.product.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * =====================================================================================
 * FILE: product-service/.../service/impl/CategoryServiceImpl.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: Implements category business logic with Redis read-through caching.
 *
 * DESIGN PATTERN / ARCHITECTURAL CONCEPT:
 * - Cache Invalidation on Mutation: Any category create/update/delete clears the
 *   categories cache (`@CacheEvict(value = "categories", allEntries = true)`).
 * =====================================================================================
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = RedisConfig.CACHE_CATEGORIES, key = "'all-active'")
    public List<CategoryResponse> getAllActiveCategories() {
        log.debug("Database Cache-Miss: Fetching all active categories from PostgreSQL");
        return categoryRepository.findAllByIsActiveTrue().stream()
                .map(categoryMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = RedisConfig.CACHE_CATEGORIES, key = "#id")
    public CategoryResponse getCategoryById(Long id) {
        log.debug("Database Cache-Miss: Fetching category by id [{}]", id);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + id));
        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = RedisConfig.CACHE_CATEGORIES, key = "#slug")
    public CategoryResponse getCategoryBySlug(String slug) {
        log.debug("Database Cache-Miss: Fetching category by slug [{}]", slug);
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with slug: " + slug));
        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional
    @CacheEvict(value = RedisConfig.CACHE_CATEGORIES, allEntries = true)
    public CategoryResponse createCategory(CategoryRequest request) {
        log.info("Creating category with name: {} and slug: {}", request.getName(), request.getSlug());

        if (categoryRepository.existsBySlug(request.getSlug())) {
            throw new DuplicateResourceException("Category with slug '" + request.getSlug() + "' already exists");
        }

        Category category = categoryMapper.toEntity(request);
        Category saved = categoryRepository.save(category);
        return categoryMapper.toResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = RedisConfig.CACHE_CATEGORIES, allEntries = true)
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        log.info("Updating category with id: {}", id);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + id));

        // If slug changed, ensure new slug is not already taken
        if (!category.getSlug().equalsIgnoreCase(request.getSlug()) && categoryRepository.existsBySlug(request.getSlug())) {
            throw new DuplicateResourceException("Category with slug '" + request.getSlug() + "' already exists");
        }

        categoryMapper.updateEntity(category, request);
        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional
    @CacheEvict(value = RedisConfig.CACHE_CATEGORIES, allEntries = true)
    public void deleteCategory(Long id) {
        log.info("Soft-deleting category with id: {}", id);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + id));
        category.setIsActive(false);
    }
}
