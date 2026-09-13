package com.ecommerce.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * =====================================================================================
 * FILE: product-service/.../ProductServiceApplication.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: Main Spring Boot bootstrap application entry point for Product Service.
 *
 * DESIGN PATTERN / ARCHITECTURAL CONCEPT:
 * - `@SpringBootApplication`: Meta-annotation enabling component scanning, auto-configuration,
 *   and configuration properties binding.
 * - `@EnableDiscoveryClient`: Registers this instance dynamically with Netflix Eureka.
 * - `@EnableCaching`: Activates Spring Cache abstraction backed by Redis.
 * - `@EnableJpaAuditing`: Activates automatic auditing for createdAt, updatedAt, createdBy, updatedBy.
 * =====================================================================================
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableCaching
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
