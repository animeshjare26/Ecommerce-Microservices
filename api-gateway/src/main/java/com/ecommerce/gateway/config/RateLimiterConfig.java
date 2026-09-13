package com.ecommerce.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * =====================================================================================
 * FILE: RateLimiterConfig.java
 * MODULE: api-gateway (Distributed Rate Limiting)
 * PURPOSE: Implements the Token-Bucket Rate Limiter algorithm backed by Redis.
 *          Protects downstream microservices against DDoS attacks and brute-force attempts.
 *
 * DESIGN PATTERN / ARCHITECTURAL MECHANISM:
 * - Token Bucket Algorithm (Stripe / AWS API Gateway Pattern).
 * - Redis-backed Distributed Key Resolver:
 *   - Authenticated Requests: Rate limited by `X-User-Id` (fair usage per customer account).
 *   - Unauthenticated Requests: Rate limited by Client IP address.
 *
 * EXECUTION FLOW POSITION:
 * - Evaluated during route execution if `RequestRateLimiter` filter is enabled.
 * - Executes atomic Redis Lua script checking bucket capacity and replenishing tokens.
 * - If bucket empty: returns HTTP 429 Too Many Requests!
 *
 * READING ORDER:
 * - Read PREVIOUS: config/CorsConfig.java
 * - Read THIS FILE: Understand Token Bucket rate limiting mechanics.
 * - Read NEXT: exception/GatewayExceptionHandler.java
 *
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Senior Question: How does the Token Bucket algorithm work internally with Redis?
 * A1: Imagine a bucket that can hold a maximum of 20 tokens (`burstCapacity`). Every second,
 *     the system adds 10 tokens (`replenishRate`) to the bucket. Each HTTP request consumes 1 token.
 *     If the bucket is empty, the request is rejected with 429!
 *     Spring Cloud Gateway executes an atomic Lua script on Redis:
 *     `redis.call('get', ...)` which computes the token delta based on the current timestamp,
 *     ensuring zero race conditions across 10 Gateway instances without distributed locks!
 *
 * Q2: Intermediate Question: Token Bucket vs. Leaky Bucket vs. Fixed Window?
 * A2: - Fixed Window (e.g. 100 req/min): Susceptible to boundary spikes (100 req at 00:59 + 100 req at 01:00 = 200 req in 2 seconds!).
 *     - Leaky Bucket: Processes requests at a strict constant rate (drops burst traffic).
 *     - Token Bucket (Our Choice): Permits legitimate short bursts of activity (e.g. page loading 15 assets)
 *       while strictly enforcing long-term average throughput!
 *
 * Q3: Beginner Intern Question: Why do we resolve by IP when `X-User-Id` is missing?
 * A3: Unauthenticated routes (like `/auth/login`) have no `X-User-Id`. If we didn't fall back
 *     to client IP address, an attacker running a dictionary password brute-force script
 *     could send 10,000 login attempts per second and overwhelm our BCrypt hasher!
 * =====================================================================================
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Resolves the rate-limiting key for each request:
     * 1. Uses `X-User-Id` if user is authenticated (prevents account-level abuse).
     * 2. Falls back to client IP address for public unauthenticated traffic.
     */
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // Check if request was authenticated and had X-User-Id injected
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }

            // Fall back to client IP address
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            String ip = (remoteAddress != null && remoteAddress.getAddress() != null)
                    ? remoteAddress.getAddress().getHostAddress()
                    : "anonymous";

            return Mono.just("ip:" + ip);
        };
    }

    /**
     * Configures the default Redis Token Bucket parameters:
     * - `replenishRate`: 10 tokens per second (average sustained traffic)
     * - `burstCapacity`: 20 tokens (maximum burst permitted in a single second)
     * - `requestedTokens`: 1 token per HTTP request
     */
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(10, 20, 1);
    }
}
