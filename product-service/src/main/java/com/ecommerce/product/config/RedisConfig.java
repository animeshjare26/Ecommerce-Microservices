package com.ecommerce.product.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * =====================================================================================
 * FILE: product-service/.../config/RedisConfig.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * WHAT DOES THIS CONFIGURATION DO AND KEY DESIGN DECISIONS:
 * -------------------------------------------------------------------------------------
 * Configures Spring's `@Cacheable` abstraction with Redis as the distributed cache.
 *
 * KEY DESIGN CHOICES:
 * 1. JSON SERIALIZATION (GenericJackson2JsonRedisSerializer):
 *    Instead of Java's native binary serialization (which is fragile, vulnerable to RCE,
 *    and unreadable in Redis CLI), we serialize cache values as clean JSON.
 *
 * 2. TIERED TIME-TO-LIVE (TTL):
 *    - Products: 10 minutes (balances freshness with fast response times).
 *    - Categories: 60 minutes (categories rarely change, so longer caching is safe).
 *
 * 3. NO NULL CACHING:
 *    `.disableCachingNullValues()` prevents non-existent product lookups from filling up
 *    Redis RAM with empty keys.
 *
 * READING ORDER:
 * - Read PREVIOUS: utils/GenericResponse.java
 * - Read THIS FILE: Understand Redis cache configuration and JSON serialization.
 * - Read NEXT: dto/response/ProductSummaryResponse.java
 *
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q: Why is Java Native Serialization (`JdkSerializationRedisSerializer`) forbidden in production?
 * A: 1. Security Vulnerability: Native Java deserialization is susceptible to Remote Code Execution (RCE)
 *       gadget chain exploits.
 *    2. Fragility: Changing a class's `serialVersionUID` or adding a field breaks existing cache entries.
 *    3. Polyglot Incompatibility: Non-Java services cannot read Java byte blobs.
 *    Using `GenericJackson2JsonRedisSerializer` stores human-readable, schema-tolerant JSON.
 * =====================================================================================
 */
@Configuration
@EnableCaching
public class RedisConfig {

    public static final String CACHE_PRODUCTS = "products";
    public static final String CACHE_CATEGORIES = "categories";

    @Bean
    public RedisCacheConfiguration defaultCacheConfiguration() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10)) // Default TTL: 10 minutes
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer(RedisCacheConfiguration defaultCacheConfiguration) {
        return builder -> builder
                // Products cache: 10 minutes TTL
                .withCacheConfiguration(CACHE_PRODUCTS,
                        defaultCacheConfiguration.entryTtl(Duration.ofMinutes(10)))
                // Categories cache: 60 minutes TTL (categories change very rarely)
                .withCacheConfiguration(CACHE_CATEGORIES,
                        defaultCacheConfiguration.entryTtl(Duration.ofMinutes(60)));
    }
}
