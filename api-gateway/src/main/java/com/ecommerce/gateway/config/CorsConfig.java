package com.ecommerce.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * =====================================================================================
 * FILE: CorsConfig.java
 * MODULE: api-gateway (CORS Edge Configuration)
 * PURPOSE: Configures Cross-Origin Resource Sharing (CORS) at the reactive Gateway edge.
 *          Handles HTTP OPTIONS preflight requests for React, Angular, and Vue SPAs.
 *
 * DESIGN PATTERN / ARCHITECTURAL MECHANISM:
 * - Centralized Edge CORS Pattern.
 * - Reactive `CorsWebFilter` from `org.springframework.web.cors.reactive`.
 *
 * EXECUTION FLOW POSITION:
 * - Executes before any route filters or controllers.
 * - If request is an HTTP `OPTIONS` preflight, the filter intercepts it and immediately
 *   returns 200 OK with allowed origin and method headers without forwarding to microservices!
 *
 * READING ORDER:
 * - Read PREVIOUS: filter/CorrelationIdFilter.java
 * - Read THIS FILE: Understand edge CORS vs microservice CORS.
 * - Read NEXT: config/RateLimiterConfig.java
 *
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Senior Question: Why must CORS be handled at the API Gateway rather than each microservice?
 * A1: The "CORS Duplication Bug"! If both the Gateway and the downstream microservice add
 *     `Access-Control-Allow-Origin: *`, the browser detects duplicate headers and rejects
 *     the request with a CORS error! By configuring CORS strictly at the Gateway edge,
 *     downstream microservices remain internal, clean, and free of browser-specific web rules.
 *
 * Q2: Intermediate Question: What is an HTTP OPTIONS preflight request?
 * A2: Browsers automatically send an HTTP OPTIONS request prior to any "non-simple" request
 *     (e.g., requests with `Authorization` headers, JSON bodies, or PUT/DELETE methods).
 *     The browser asks the server: *"Am I allowed to send a POST request with Authorization header from localhost:3000?"*
 *     If the server replies with matching `Access-Control-Allow-*` headers, the browser sends the actual request!
 *
 * Q3: Beginner Intern Question: What does `configuration.setAllowCredentials(true)` do?
 * A3: It allows browsers to send and receive HTTP cookies (like `JSESSIONID` or refresh cookies)
 *     across different domains. When `allowCredentials` is true, the `allowedOrigins` CANNOT
 *     be `*` (wildcard); you must explicitly list allowed domain origins for browser security!
 * =====================================================================================
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        
        // Allowed frontend client origins (React, Vite, Next.js)
        corsConfig.setAllowedOriginPatterns(List.of(
                "http://localhost:3000",
                "http://localhost:5173",
                "http://127.0.0.1:3000",
                "http://127.0.0.1:5173"
        ));
        
        // Allowed HTTP methods
        corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        
        // Allowed HTTP headers
        corsConfig.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Correlation-Id",
                "Accept",
                "Origin",
                "X-Requested-With"
        ));
        
        // Headers exposed to the browser JavaScript client
        corsConfig.setExposedHeaders(List.of("X-Correlation-Id", "Authorization"));
        
        corsConfig.setAllowCredentials(true);
        corsConfig.setMaxAge(3600L); // Cache preflight response in browser for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}
