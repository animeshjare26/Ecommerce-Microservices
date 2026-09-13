package com.ecommerce.product.controller;

import com.ecommerce.product.config.WebConfig;
import com.ecommerce.product.dto.response.PageResponse;
import com.ecommerce.product.dto.response.ProductDetailResponse;
import com.ecommerce.product.dto.response.ProductSummaryResponse;
import com.ecommerce.product.enums.StockStatus;
import com.ecommerce.product.security.RoleAuthorizationInterceptor;
import com.ecommerce.product.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * =====================================================================================
 * FILE: product-service/.../controller/ProductControllerTest.java
 * PURPOSE: WebMvc slice test verifying public endpoint accessibility and downstream role authorization.
 * =====================================================================================
 */
@WebMvcTest(ProductController.class)
@Import({WebConfig.class, RoleAuthorizationInterceptor.class})
@ActiveProfiles("test")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @Test
    @DisplayName("Public GET /products should succeed without authentication headers")
    void testPublicGetAllProducts() throws Exception {
        ProductSummaryResponse summary = ProductSummaryResponse.builder()
                .id(1L)
                .sku("SKU-100")
                .name("Demo Item")
                .price(new BigDecimal("19.99"))
                .stockStatus(StockStatus.IN_STOCK)
                .build();

        PageResponse<ProductSummaryResponse> pageResponse = PageResponse.<ProductSummaryResponse>builder()
                .content(List.of(summary))
                .pageNumber(0)
                .pageSize(20)
                .totalElements(1)
                .totalPages(1)
                .isLast(true)
                .build();

        when(productService.getAllProducts(any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/products")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].sku").value("SKU-100"));
    }

    @Test
    @DisplayName("POST /products without ROLE_ADMIN or ROLE_SELLER should return HTTP 403 Forbidden")
    void testPostProductUnauthorized() throws Exception {
        String newProductJson = """
                {
                    "sku": "NEW-SKU-999",
                    "name": "Unauthorized Product",
                    "price": 99.99,
                    "categoryId": 1
                }
                """;

        // Calling with ROLE_USER should be blocked
        mockMvc.perform(post("/products")
                        .header("X-User-Id", "123")
                        .header("X-User-Roles", "[ROLE_USER]")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newProductJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /products with ROLE_SELLER should succeed and return HTTP 201 Created")
    void testPostProductAuthorizedSeller() throws Exception {
        String newProductJson = """
                {
                    "sku": "SELLER-SKU-1",
                    "name": "Seller Brand T-Shirt",
                    "price": 29.99,
                    "stockQuantity": 100,
                    "lowStockThreshold": 10,
                    "categoryId": 1
                }
                """;

        ProductDetailResponse detailResponse = ProductDetailResponse.builder()
                .id(50L)
                .sku("SELLER-SKU-1")
                .name("Seller Brand T-Shirt")
                .price(new BigDecimal("29.99"))
                .stockStatus(StockStatus.IN_STOCK)
                .sellerId(42L)
                .build();

        when(productService.createProduct(any(), eq(42L))).thenReturn(detailResponse);

        mockMvc.perform(post("/products")
                        .header("X-User-Id", "42")
                        .header("X-User-Roles", "[ROLE_SELLER]")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newProductJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sku").value("SELLER-SKU-1"));
    }
}
