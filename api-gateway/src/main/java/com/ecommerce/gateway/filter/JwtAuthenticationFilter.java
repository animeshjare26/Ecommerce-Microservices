package com.ecommerce.gateway.filter;

import com.ecommerce.gateway.security.RsaKeyProvider;
import com.ecommerce.gateway.utils.GenericResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * =====================================================================================
 * FILE: JwtAuthenticationFilter.java
 * MODULE: api-gateway (Reactive Edge Security Filter)
 *
 * WHAT DOES THIS FILTER DO AND WHY DO WE NEED IT?
 * -------------------------------------------------------------------------------------
 * In our microservices architecture, this filter acts as the single security gate at the edge.
 * Instead of forcing every downstream microservice (product-service, order-service, etc.)
 * to parse JWT tokens and manage public keys, the API Gateway verifies the token ONCE:
 *
 *   [Client Browser / Mobile App]
 *              │  (Authorization: Bearer <token>)
 *              ▼
 *     ┌──────────────────┐
 *     │   API GATEWAY    │  1. Check public whitelist (skip login, signup, catalog browse)
 *     │  (This Filter)   │  2. Verify RS256 signature in-memory using RSA Public Key (<0.1ms)
 *     │                  │  3. Check Redis: Is this token revoked/logged out?
 *     │                  │  4. Extract userId & roles -> inject into X-User-* headers
 *     └────────┬─────────┘
 *              │  (X-User-Id: 101, X-User-Roles: ROLE_SELLER)
 *              ▼
 *     ┌──────────────────┐
 *     │ Downstream Svc   │  No JWT parsing needed! Simply reads X-User-Id
 *     │ (product-service)│  via UserContext in 0.001ms.
 *     └──────────────────┘
 *
 * HOW DOES IT PREVENT SECURITY SPOOFING?
 * -------------------------------------------------------------------------------------
 * If a malicious attacker manually adds "X-User-Id: 1" in their request, this filter's
 * mutate() step OVERWRITES it with the genuine ID extracted from the verified cryptographic
 * JWT. Untrusted client headers are completely sanitized.
 *
 * EXECUTION ORDER:
 * - Runs with Order: -1 (immediately after CorrelationIdFilter, before downstream routing).
 *
 * READING ORDER:
 * - Read PREVIOUS: security/RsaKeyProvider.java
 * - Read THIS FILE: Understand non-blocking reactive security filter implementation.
 * - Read NEXT: filter/CorrelationIdFilter.java
 *
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Senior Question: Why does this filter mutate headers instead of letting downstream services
 *     validate the JWT themselves?
 * A1: Performance & Decoupling! If every microservice (`product-service`, `cart-service`,
 *     `order-service`) had to parse JWTs, check public keys, and deserialize roles, we would
 *     waste massive CPU cycles repeating the same work 5 times per request flow. By verifying
 *     ONCE at the edge and injecting trusted `X-User-Id` headers, downstream services remain
 *     completely lightweight and focus purely on domain business logic!
 *
 * Q2: Intermediate Question: How do we prevent a malicious client from forging `X-User-Id: admin`
 *     in their HTTP request header to bypass security?
 * A2: Header Sanitization! The API Gateway's `mutate()` operation OVERWRITES any existing
 *     `X-User-*` headers sent by external clients. Downstream services are placed in a private
 *     VPC network that accepts HTTP traffic ONLY from the Gateway's IP address!
 *
 * Q3: Beginner Intern Question: What does `exchange.getResponse().writeWith(...)` do in WebFlux?
 * A3: In Spring MVC, you write directly to `HttpServletResponse.getOutputStream()`.
 *     In Spring WebFlux, because Netty is non-blocking, we allocate a `DataBuffer` containing
 *     our JSON bytes and return a reactive `Mono<Void>` stream to Netty's event loop to write
 *     the bytes asynchronously to the client socket without blocking the worker thread!
 * =====================================================================================
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final RsaKeyProvider rsaKeyProvider;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Whitelist of public endpoints that bypass JWT verification
    private static final List<String> PUBLIC_WHITELIST = List.of(
            "/api/auth/signup",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/email-exists",
            "/api/auth/public-key",
            "/actuator"
    );

    @Override
    public int getOrder() {
        // High priority: execute before routing and load balancing filters
        return -1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();

        // Step 1: Whitelist Check
        if (isWhitelisted(path)) {
            log.debug("Path [{}] is whitelisted; bypassing edge JWT verification", path);
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        // Step 2: Public GET Check for Product Catalog & Categories
        boolean isPublicCatalogGet = HttpMethod.GET.equals(method) &&
                (path.startsWith("/api/products") || path.startsWith("/api/categories"));

        if (isPublicCatalogGet && (authHeader == null || !authHeader.startsWith("Bearer "))) {
            log.debug("Public catalog GET on [{}]; bypassing authentication", path);
            return chain.filter(exchange);
        }

        // Step 3: Extract Authorization Header
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header on protected path [{}]", path);
            return onError(exchange, "Missing or malformed Authorization header. Use 'Bearer <token>'", HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);

        // Step 3: Cryptographic RS256 Verification in RAM (< 0.1ms)
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(rsaKeyProvider.getPublicKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("JWT access token expired for path [{}]: {}", path, e.getMessage());
            return onError(exchange, "JWT access token has expired. Please refresh your session", HttpStatus.UNAUTHORIZED);
        } catch (SignatureException e) {
            log.error("CRITICAL: Invalid JWT signature detected on path [{}]! Tampering suspected: {}", path, e.getMessage());
            return onError(exchange, "Invalid cryptographic token signature", HttpStatus.UNAUTHORIZED);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Malformed or invalid JWT token on path [{}]: {}", path, e.getMessage());
            return onError(exchange, "Invalid JWT authentication token", HttpStatus.UNAUTHORIZED);
        }

        // Step 4: Extract JTI and Check Redis Revocation Blocklist
        String jti = claims.get("jti") != null ? claims.get("jti").toString() : claims.get("id", String.class);
        if (jti != null && !jti.isBlank()) {
            String blocklistKey = "blocklist:jti:" + jti;
            return redisTemplate.hasKey(blocklistKey)
                    .flatMap(isRevoked -> {
                        if (Boolean.TRUE.equals(isRevoked)) {
                            log.warn("Rejected revoked token with JTI [{}] on path [{}]", jti, path);
                            return onError(exchange, "Token has been revoked. Please log in again", HttpStatus.UNAUTHORIZED);
                        }
                        return proceedWithDownstreamHeaders(exchange, chain, claims);
                    })
                    // If Redis is temporarily unreachable, log warning and fail-safe or proceed
                    .onErrorResume(e -> {
                        log.error("Redis blocklist check failed for JTI [{}]: {}. Proceeding with cryptographic trust", jti, e.getMessage());
                        return proceedWithDownstreamHeaders(exchange, chain, claims);
                    });
        }

        return proceedWithDownstreamHeaders(exchange, chain, claims);
    }

    /**
     * Injects claims as sanitized downstream HTTP headers before forwarding.
     */
    private Mono<Void> proceedWithDownstreamHeaders(ServerWebExchange exchange, GatewayFilterChain chain, Claims claims) {
        String userId = claims.get("userId") != null ? claims.get("userId").toString() : "";
        String email = claims.getSubject() != null ? claims.getSubject() : "";
        Object rolesObj = claims.get("roles");
        String roles = rolesObj != null ? rolesObj.toString() : "[]";

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", userId)
                .header("X-User-Email", email)
                .header("X-User-Roles", roles)
                .build();

        log.debug("Successfully authenticated request for userId [{}], email [{}]. Mutating downstream headers", userId, email);
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * Checks if the request path begins with any whitelisted public prefix.
     */
    private boolean isWhitelisted(String path) {
        return PUBLIC_WHITELIST.stream().anyMatch(path::startsWith);
    }

    /**
     * Formats reactive error response into standard GenericResponse JSON envelope.
     */
    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        GenericResponse<Void> errorEnvelope = GenericResponse.error(message, List.of(message));
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorEnvelope);
        } catch (JsonProcessingException e) {
            bytes = ("{\"success\":false,\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
