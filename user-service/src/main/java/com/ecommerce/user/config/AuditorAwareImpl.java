package com.ecommerce.user.config;

import com.ecommerce.user.security.services.UserDetailsImpl;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * =====================================================================================
 * FILE: AuditorAwareImpl.java
 * MODULE: user-service
 * PURPOSE: Resolves the currently authenticated user's ID for JPA Auditing (@CreatedBy, @LastModifiedBy).
 * 
 * DESIGN PATTERN: SPI (Service Provider Interface) implementation for Spring Data JPA.
 * 
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: How does `@EnableJpaAuditing` work under the hood?
 * A1: Spring Data registers an `AuditingEntityListener` on entities marked with 
 *     `@EntityListeners(AuditingEntityListener.class)`. Before `persist()` or `update()`, 
 *     Hibernate interceptors invoke this AuditorAware bean to dynamically populate 
 *     audit fields (timestamps and user IDs) without manual setter calls.
 * =====================================================================================
 */
@Component("auditorAware")
public class AuditorAwareImpl implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() 
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return Optional.empty();
        }

        if (authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
            return Optional.ofNullable(userDetails.getId());
        }

        return Optional.empty();
    }
}
