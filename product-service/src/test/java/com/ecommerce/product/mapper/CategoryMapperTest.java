package com.ecommerce.product.mapper;

import com.ecommerce.product.dto.request.CategoryRequest;
import com.ecommerce.product.dto.response.CategoryResponse;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.mapper.category.CategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryMapperTest {

    private CategoryMapper categoryMapper;

    @BeforeEach
    void setUp() {
        categoryMapper = new CategoryMapper();
    }

    @Test
    @DisplayName("Should map CategoryRequest to Category entity for creation")
    void testToEntity() {
        CategoryRequest request = CategoryRequest.builder()
                .name("  Home & Kitchen  ")
                .slug("  HOME-KITCHEN  ")
                .description("Appliances and utensils")
                .isActive(true)
                .build();

        Category entity = categoryMapper.toEntity(request);

        assertThat(entity).isNotNull();
        assertThat(entity.getName()).isEqualTo("Home & Kitchen");
        assertThat(entity.getSlug()).isEqualTo("home-kitchen");
        assertThat(entity.getDescription()).isEqualTo("Appliances and utensils");
        assertThat(entity.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("Should update existing Category entity with new values")
    void testUpdateEntity() {
        Category entity = Category.builder()
                .id(1L)
                .name("Old Name")
                .slug("old-name")
                .description("Old Description")
                .isActive(true)
                .build();

        CategoryRequest request = CategoryRequest.builder()
                .name("  New Name  ")
                .slug("  NEW-NAME  ")
                .description("New Description")
                .isActive(false)
                .build();

        categoryMapper.updateEntity(entity, request);

        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getName()).isEqualTo("New Name");
        assertThat(entity.getSlug()).isEqualTo("new-name");
        assertThat(entity.getDescription()).isEqualTo("New Description");
        assertThat(entity.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("Should map Category entity to CategoryResponse DTO")
    void testToResponse() {
        Category entity = Category.builder()
                .id(5L)
                .name("Electronics")
                .slug("electronics")
                .description("Gadgets and computers")
                .isActive(true)
                .build();

        CategoryResponse response = categoryMapper.toResponse(entity);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(5L);
        assertThat(response.getName()).isEqualTo("Electronics");
        assertThat(response.getSlug()).isEqualTo("electronics");
        assertThat(response.getDescription()).isEqualTo("Gadgets and computers");
        assertThat(response.getIsActive()).isTrue();
    }
}
