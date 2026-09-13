package com.ecommerce.product.mapper.product;

import com.ecommerce.product.dto.request.ProductCreateRequest;
import com.ecommerce.product.dto.request.ProductUpdateRequest;
import com.ecommerce.product.dto.response.ProductDetailResponse;
import com.ecommerce.product.dto.response.ProductSummaryResponse;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.enums.StockStatus;
import org.springframework.stereotype.Component;

/**
 * =====================================================================================
 * FILE: product-service/.../mapper/product/ProductMapper.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: Service-wise mapper component for Product domain.
 *          Handles DTO -> Entity (Create / Update) and Entity -> DTO (Get).
 * =====================================================================================
 */
@Component
public class ProductMapper {

    /**
     * Maps a ProductCreateRequest DTO and domain context to a new Product entity.
     */
    public Product toEntity(ProductCreateRequest request,
                            Category category,
                            Long sellerId,
                            StockStatus stockStatus) {
        if (request == null) {
            return null;
        }

        Long stockQuantity = request.getStockQuantity() != null ? request.getStockQuantity() : 0L;
        Long lowStockThreshold = request.getLowStockThreshold() != null ? request.getLowStockThreshold() : 10L;

        return Product.builder()
                .sku(request.getSku() != null ? request.getSku().trim().toUpperCase() : null)
                .name(request.getName() != null ? request.getName().trim() : null)
                .description(request.getDescription())
                .price(request.getPrice())
                .stockQuantity(stockQuantity)
                .lowStockThreshold(lowStockThreshold)
                .stockStatus(stockStatus != null ? stockStatus : StockStatus.IN_STOCK)
                .sellerId(sellerId != null ? sellerId : 1L)
                .category(category)
                .isActive(true)
                .build();
    }

    /**
     * Updates an existing Product entity from a ProductUpdateRequest DTO.
     */
    public void updateEntity(Product product,
                             ProductUpdateRequest request,
                             Category newCategory,
                             StockStatus newStockStatus) {
        if (product == null || request == null) {
            return;
        }

        if (request.getName() != null) {
            product.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getStockQuantity() != null) {
            product.setStockQuantity(request.getStockQuantity());
        }
        if (request.getLowStockThreshold() != null) {
            product.setLowStockThreshold(request.getLowStockThreshold());
        }
        if (newStockStatus != null) {
            product.setStockStatus(newStockStatus);
        }
        if (newCategory != null) {
            product.setCategory(newCategory);
        }
        if (request.getIsActive() != null) {
            product.setIsActive(request.getIsActive());
        }
    }

    /**
     * Maps a Product JPA entity to a ProductSummaryResponse DTO for catalog browse & search.
     */
    public ProductSummaryResponse toSummaryResponse(Product product) {
        if (product == null) {
            return null;
        }

        return ProductSummaryResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .price(product.getPrice())
                .stockStatus(product.getStockStatus())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .categorySlug(product.getCategory() != null ? product.getCategory().getSlug() : null)
                .sellerId(product.getSellerId())
                .build();
    }

    /**
     * Maps a Product JPA entity to a ProductDetailResponse DTO for single product detail page.
     * Implements event dampening principle: surfaces display count and urgency message only
     * when stock drops to or below the low stock threshold.
     */
    public ProductDetailResponse toDetailResponse(Product product) {
        if (product == null) {
            return null;
        }

        Long displayCount = null;
        String urgencyMessage = null;

        if (product.getStockStatus() == StockStatus.LOW_STOCK) {
            displayCount = product.getStockQuantity();
            urgencyMessage = "Only " + displayCount + " left in stock - order soon!";
        }

        return ProductDetailResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stockStatus(product.getStockStatus())
                .displayStockCount(displayCount)
                .urgencyMessage(urgencyMessage)
                .lowStockThreshold(product.getLowStockThreshold())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .categorySlug(product.getCategory() != null ? product.getCategory().getSlug() : null)
                .sellerId(product.getSellerId())
                .isActive(product.getIsActive())
                .createdBy(product.getCreatedBy())
                .updatedBy(product.getUpdatedBy())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
