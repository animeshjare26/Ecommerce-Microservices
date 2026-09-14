package com.ecommerce.product.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Set;

/**
 * =====================================================================================
 * FILE: product-service/.../security/UserContext.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 *
 * WHAT IS THIS CLASS AND WHY DO WE NEED IT?
 * -------------------------------------------------------------------------------------
 * Stores the authenticated user's identity (`userId`, `email`, `roles`) for the duration
 * of the current HTTP request.
 *
 * WHY USE THREADLOCAL?
 * 1. CLEAN ARCHITECTURE:
 *    Instead of passing `HttpServletRequest` or `userId` as an argument through every single
 *    controller, service, and repository method, code anywhere on the current request thread
 *    can simply call `UserContext.getUserId()`.
 *
 * 2. CRITICAL SAFETY RULE (MUST CALL clear()):
 *    Tomcat reuses worker threads from a thread pool. When an HTTP request completes,
 *    `UserContext.clear()` MUST be called. Otherwise, a subsequent user request assigned
 *    to that same thread would accidentally inherit the previous user's credentials!
 * =====================================================================================
 */
public class UserContext {

    private static final ThreadLocal<UserIdentity> CURRENT_USER = new ThreadLocal<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserIdentity {
        private Long userId;
        private String email;
        @Builder.Default
        private Set<String> roles = Collections.emptySet();

        public boolean hasRole(String role) {
            return roles != null && roles.contains(role);
        }

        public boolean isAdmin() {
            return hasRole("ROLE_ADMIN");
        }

        public boolean isSeller() {
            return hasRole("ROLE_SELLER");
        }
    }

    public static void set(UserIdentity identity) {
        CURRENT_USER.set(identity);
    }

    public static UserIdentity get() {
        return CURRENT_USER.get();
    }

    public static Long getUserId() {
        UserIdentity identity = CURRENT_USER.get();
        return identity != null ? identity.getUserId() : null;
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
