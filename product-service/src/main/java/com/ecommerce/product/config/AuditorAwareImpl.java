package com.ecommerce.product.config;

import com.ecommerce.product.security.UserContext;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * =====================================================================================
 * FILE: product-service/.../config/AuditorAwareImpl.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: Resolves the currently authenticated user's ID for JPA Auditing (@CreatedBy, @LastModifiedBy).
 *
 * DESIGN PATTERN: SPI (Service Provider Interface) implementation for Spring Data JPA.
 *
 * INTEGRATION:
 * - Reads `userId` directly from `UserContext`, which was extracted from the API Gateway's
 *   `X-User-Id` downstream header.
 * =====================================================================================
 */
@Component("auditorAware")
public class AuditorAwareImpl implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        Long userId = UserContext.getUserId();
        return Optional.ofNullable(userId);
    }
}
