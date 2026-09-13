package com.ecommerce.product.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * =====================================================================================
 * FILE: product-service/.../config/JpaAuditingConfig.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: Configures Spring Data JPA Auditing with the AuditorAware bean.
 *
 * DESIGN PATTERN / ARCHITECTURAL CONCEPT:
 * - Separation of Concerns: Dedicated configuration for persistence auditing.
 *
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q: Why must `@EnableJpaAuditing` NOT be placed on `@SpringBootApplication`?
 * A: Placing `@EnableJpaAuditing` on `@SpringBootApplication` breaks `@WebMvcTest` slice tests!
 *    `@WebMvcTest` bootstraps the `@SpringBootApplication` class but excludes JPA/Hibernate
 *    infrastructure. When Spring attempts to initialize `AuditingEntityListener` without a JPA
 *    metamodel, the test context crashes with `BeanCreationException: JPA metamodel must not be empty`.
 *    Keeping `@EnableJpaAuditing` in a dedicated `@Configuration` maintains clean test boundaries.
 * =====================================================================================
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {
}
