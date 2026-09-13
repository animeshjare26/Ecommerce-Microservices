package com.ecommerce.product.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * =====================================================================================
 * FILE: product-service/.../config/OpenApiConfig.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: Configures OpenAPI 3.0 / Swagger documentation metadata for product catalog APIs.
 * =====================================================================================
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI productServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Product Catalog Microservice API")
                        .description("High-performance product catalog with category hierarchies, Redis read-through caching, and N+1 query elimination.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("E-Commerce Architecture Team")
                                .email("dev@ecommerce.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://spring.io")));
    }
}
