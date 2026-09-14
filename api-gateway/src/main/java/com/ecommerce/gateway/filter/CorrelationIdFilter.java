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
 *
 * WHAT IS A CORRELATION ID AND WHY DO WE NEED IT?
 * -------------------------------------------------------------------------------------
 * In a monolith, debugging is easy: all logs are in one file on one server.
 * In microservices, a single user action (e.g. clicking "Place Order") triggers a chain
 * of calls across multiple independent servers:
 *
 *   User Click
 *       │
 *       ▼
 *   [API Gateway] ──► [Order Service] ──► [Inventory Service] ──► [Payment Service]
 *
 * At peak traffic, servers generate MILLIONS of log lines every minute.
 * If an order fails with a generic "500 Internal Server Error", searching by timestamp
 * is useless because thousands of requests happen in the exact same millisecond.
 * Finding which log line in Payment Service belongs to user #123 is like finding a
 * needle in a haystack.
 *
 * HOW DOES THIS FILTER SOLVE THE PROBLEM?
 * -------------------------------------------------------------------------------------
 * 1. GENERATE OR PRESERVE A UNIQUE ID:
 *    When a request hits the Gateway, this filter checks for an `X-Correlation-Id` header.
 *    If missing, it generates a fresh UUID (e.g., `abc-123`).
 *
 * 2. PASS DOWNSTREAM TO ALL MICROSERVICES:
 *    It injects `X-Correlation-Id: abc-123` into the HTTP request headers sent to downstream
 *    services (user-service, product-service, order-service, etc.).
 *    Every downstream service logs this ID alongside every log statement.
 *
 * 3. ECHO BACK TO CLIENT IN HTTP RESPONSE:
 *    It attaches `X-Correlation-Id: abc-123` to the response header sent back to the browser/app.
 *
 * REAL-WORLD BENEFITS:
 * -------------------------------------------------------------------------------------
 * A) ONE-QUERY DISTRIBUTED DEBUGGING:
 *    In log aggregators (ELK, Splunk, Grafana Loki, CloudWatch), you simply query:
 *       correlationId = "abc-123"
 *    You immediately see the entire sequential journey of that specific request:
 *       [Gateway]           INFO: Ingress POST /api/orders [X-Correlation-Id: abc-123]
 *       [Order-Service]     INFO: Creating order #5002 [X-Correlation-Id: abc-123]
 *       [Inventory-Service] INFO: Reserved stock for SKU-LAPTOP [X-Correlation-Id: abc-123]
 *       [Payment-Service]   ERROR: Card declined: Insufficient funds [X-Correlation-Id: abc-123]
 *       [Order-Service]     WARN: Rolling back order #5002 [X-Correlation-Id: abc-123]
 *    Root cause discovered in 10 seconds!
 *
 * B) INSTANT CUSTOMER SUPPORT:
 *    If an error occurs, the frontend displays: "Something went wrong. Ref ID: abc-123".
 *    The customer gives that ID to support, and developers find the exact error instantly.
 *
 * EXECUTION ORDER:
 * - Runs with `Ordered.HIGHEST_PRECEDENCE` (first filter) so every subsequent filter
 *   and log statement has access to this correlation ID.
 *
 * READING ORDER:
 * - Read PREVIOUS: filter/JwtAuthenticationFilter.java
 * - Read THIS FILE: Understand distributed tracing mechanics.
 * - Read NEXT: config/CorsConfig.java
 *
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Senior Question: Why is a Correlation ID essential in a microservices ecosystem?
 * A1: Without a correlation ID, debugging a failed user checkout is impossible.
 *     A single click on "Place Order" spans multiple microservices (`Gateway` -> `Order` -> `Inventory` -> `Payment`).
 *     Searching through millions of log lines across separate servers is needle-in-a-haystack work.
 *     By tagging all log statements with `X-Correlation-Id`, engineers can query log aggregators
 *     with `correlationId="abc-123"` and view the exact end-to-end distributed story in sequential order!
 *
 * Q2: Intermediate Question: Why do we also write `X-Correlation-Id` back to the HTTP response header?
 * A2: Client-side observability! When a customer encounters an error ("Something went wrong"),
 *     the frontend displays: *"Error Reference: abc-123"*. When the user contacts customer support,
 *     the support team looks up that reference ID in the server logs to instantly identify the root cause!
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
