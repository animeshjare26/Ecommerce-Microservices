package com.ecommerce.product.security;

import com.ecommerce.product.exception.UnauthorizedActionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * =====================================================================================
 * FILE: product-service/.../security/RoleAuthorizationInterceptor.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: Inspects downstream gateway headers (`X-User-Id`, `X-User-Roles`) and enforces
 *          role-based access control for mutating operations.
 *
 * DESIGN PATTERN / ARCHITECTURAL CONCEPT:
 * - Edge-to-Downstream Trust Model: The API Gateway performs cryptographic RS256 token
 *   validation and passes trusted claims as headers. The downstream microservice enforces
 *   fine-grained authorization (RBAC) without re-validating the JWT.
 *
 * READING ORDER:
 * - Read PREVIOUS: security/UserContext.java
 * - Read THIS FILE: Understand downstream role verification.
 * - Read NEXT: config/WebConfig.java
 * =====================================================================================
 */
@Component
@Slf4j
public class RoleAuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        String userIdHeader = request.getHeader("X-User-Id");
        String emailHeader = request.getHeader("X-User-Email");
        String rolesHeader = request.getHeader("X-User-Roles");

        Set<String> roles = parseRoles(rolesHeader);
        Long userId = parseUserId(userIdHeader);

        UserContext.set(UserContext.UserIdentity.builder()
                .userId(userId)
                .email(emailHeader)
                .roles(roles)
                .build());

        // GET requests are public for catalog browsing
        if ("GET".equalsIgnoreCase(method)) {
            return true;
        }

        // State-mutating methods (POST, PUT, DELETE) require authorization
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            log.debug("Enforcing authorization for [{}] {} with roles {}", method, path, roles);

            if (path.contains("/categories")) {
                // Category management is strictly restricted to platform ADMINs
                if (!roles.contains("ROLE_ADMIN")) {
                    throw new UnauthorizedActionException("Category management requires ROLE_ADMIN privileges");
                }
            } else if (path.contains("/products")) {
                // Product management is permitted for ADMINs and SELLERs
                if (!roles.contains("ROLE_ADMIN") && !roles.contains("ROLE_SELLER")) {
                    throw new UnauthorizedActionException("Product management requires ROLE_ADMIN or ROLE_SELLER privileges");
                }
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // Prevent ThreadLocal memory leak in pooled threads!
        UserContext.clear();
    }

    private Set<String> parseRoles(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return Collections.emptySet();
        }
        // Normalize: e.g. "[ROLE_ADMIN, ROLE_USER]" -> "ROLE_ADMIN", "ROLE_USER"
        String cleaned = rolesHeader.replaceAll("[\\[\\]\"]", "").trim();
        if (cleaned.isEmpty()) {
            return Collections.emptySet();
        }
        return Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private Long parseUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userIdHeader.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid X-User-Id header value: {}", userIdHeader);
            return null;
        }
    }
}
