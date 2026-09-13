package com.ecommerce.user.config;

import com.ecommerce.user.enums.TokenType;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.EnumMap;
import java.util.Map;

/**
 * =====================================================================================
 * FILE: JwtConfig.java
 * MODULE: user-service
 * PURPOSE: Binds application.yml `jwt.*` properties into type-safe configuration objects.
 * 
 * DESIGN PATTERN: Type-Safe Configuration Properties Pattern.
 * 
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Why use `@ConfigurationProperties` instead of multiple `@Value` annotations?
 * A1: `@ConfigurationProperties` provides hierarchical, type-safe binding, supports complex 
 *     nested structures, validates configurations on startup, and avoids repeating property 
 *     strings across multiple `@Value("${jwt.access.secret}")` annotations.
 * 
 * Q2: Why use `EnumMap` instead of `HashMap` for enum keys?
 * A2: Performance! `EnumMap` is internally represented as an extremely compact, blazing-fast 
 *     single Java array with zero hashing collisions and O(1) direct array indexing.
 * =====================================================================================
 */
@Configuration
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtConfig {

    private TokenConfig refresh;
    private TokenConfig access;
    private TokenConfig forgotPassword;

    // Fast O(1) lookup table indexed by TokenType enum
    private Map<TokenType, TokenConfig> tokenConfigMap;

    @PostConstruct
    private void init() {
        tokenConfigMap = new EnumMap<>(TokenType.class);
        tokenConfigMap.put(TokenType.REFRESH_TOKEN, refresh);
        tokenConfigMap.put(TokenType.ACCESS_TOKEN, access);
        tokenConfigMap.put(TokenType.FORGOT_PASSWORD, forgotPassword != null ? forgotPassword : access);
    }

    public TokenConfig getTokenConfigByType(TokenType tokenType) {
        return tokenConfigMap.get(tokenType);
    }

    @Data
    public static class TokenConfig {
        private String secret;
        private long expiration; // Expiration time in minutes
    }
}
