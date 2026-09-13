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
 * PURPOSE: ThreadLocal holder for downstream user identity propagated by API Gateway.
 *
 * DESIGN PATTERN / ARCHITECTURAL CONCEPT:
 * - Thread-Local Storage Pattern: Allows service layers to access authenticated caller
 *   identity (`userId`, `roles`) without polluting every method signature with HttpServletRequest.
 *
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q: Why is `UserContext.clear()` mandatory inside an `afterCompletion()` interceptor?
 * A: Application servers (Tomcat) use thread pools. If you don't call `ThreadLocal.remove()`
 *    when the request finishes, the pooled worker thread retains the previous user's credentials!
 *    A subsequent request allocated to the same thread could leak identity or inherit permissions
 *    (ThreadLocal memory leak and security breach).
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
