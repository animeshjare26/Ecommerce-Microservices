package com.ecommerce.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * =====================================================================================
 * FILE: CorrelationIdFilter.java
 * MODULE: api-gateway (Distributed Tracing Filter)
 * PURPOSE: Inspects incoming HTTP requests for an `X-Correlation-Id` header.
 *          If absent, generates a new random UUID. Injects the correlation ID into both
 *          the downstream request headers and the HTTP client response headers.
 *
 * DESIGN PATTERN / ARCHITECTURAL MECHANISM:
 * - Correlation Identifier Pattern (Enterprise Integration Patterns).
 * - Distributed Tracing Foundation: Unifies log traces across microservices.
 *
 * EXECUTION FLOW POSITION:
 * - Order: `Ordered.HIGHEST_PRECEDENCE` (Runs first before all other filters).
 * - Step 1: Reads `X-Correlation-Id`.
 * - Step 2: Injects into request headers (`X-Correlation-Id: abc-123`).
 * - Step 3: Adds callback to append `X-Correlation-Id` into client response headers.
 *
 * READING ORDER:
 * - Read PREVIOUS: filter/JwtAuthenticationFilter.java
 * - Read THIS FILE: Understand distributed tracing mechanics.
 * - Read NEXT: config/CorsConfig.java
 *
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Senior Question: Why is a Correlation ID essential in a microservices ecosystem?
 * A1: Without a correlation ID, debugging a failed user checkout is impossible.
 *     A single click on "Place Order" spans 5 microservices (`Gateway` -> `Order` -> `Inventory` -> `Payment` -> `Notification`).
 *     If payment fails, searching through millions of log lines across 5 separate servers
 *     is needle-in-a-haystack work. By tagging all log statements with `X-Correlation-Id`,
 *     engineers can query Splunk / ELK / Grafana Loki: `correlationId="abc-123"` and view the
 *     exact end-to-end distributed story in sequential order!
 *
 * Q2: Intermediate Question: Why do we also write `X-Correlation-Id` back to the HTTP response header?
 * A2: Client-side observability! When a customer encounters an error ("Something went wrong"),
 *     the frontend React app displays: *"Error Reference: abc-123"*.
 *     When the user contacts customer support, the support team looks up that reference ID in
 *     the server logs to instantly identify the root cause!
 *
 * Q3: Beginner Intern Question: What is the difference between a `TraceId` and a `CorrelationId`?
 * A3: In OpenTelemetry / Micrometer Tracing, a `TraceId` identifies the entire end-to-end journey,
 *     and `SpanId` identifies an individual unit of work within one microservice.
 *     A `CorrelationId` is a lightweight, human-readable HTTP header equivalent that accomplishes
 *     the same cross-service log linking without requiring a complex tracing backend like Zipkin!
 * =====================================================================================
 */
@Component
@Slf4j
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    public int getOrder() {
        // Run first so every subsequent filter and log statement has access to the correlation ID
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            log.debug("Generated new Correlation ID: [{}]", correlationId);
        } else {
            log.debug("Found existing Correlation ID in request: [{}]", correlationId);
        }

        final String finalCorrelationId = correlationId;

        // Step 1: Mutate downstream request headers
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(CORRELATION_ID_HEADER, finalCorrelationId)
                .build();

        // Step 2: Mutate response headers so client receives the tracking ID
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, finalCorrelationId);
            return Mono.empty();
        });

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }
}
