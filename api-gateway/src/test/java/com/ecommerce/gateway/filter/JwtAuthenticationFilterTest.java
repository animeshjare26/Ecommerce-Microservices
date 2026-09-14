package com.ecommerce.gateway.filter;

import com.ecommerce.gateway.security.RsaKeyProvider;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * =====================================================================================
 * FILE: JwtAuthenticationFilterTest.java
 * MODULE: api-gateway (Unit Test Suite)
 * PURPOSE: Verifies reactive RS256 token verification, JTI Redis revocation checks,
 *          whitelisting, and downstream header injection using Mockito and StepVerifier.
 * =====================================================================================
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private RsaKeyProvider rsaKeyProvider;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private GatewayFilterChain chain;

    private JwtAuthenticationFilter filter;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        // Generate an ephemeral RSA 2048 keypair for cryptographic testing
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        this.keyPair = kpg.generateKeyPair();

        lenient().when(rsaKeyProvider.getPublicKey()).thenReturn(keyPair.getPublic());
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());

        this.filter = new JwtAuthenticationFilter(rsaKeyProvider, redisTemplate);
    }

    @Test
    @DisplayName("filter() - Whitelisted path should bypass JWT verification")
    void testWhitelistedPath_BypassesAuth() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, times(1)).filter(exchange);
        verifyNoInteractions(rsaKeyProvider);
    }

    @Test
    @DisplayName("filter() - Protected path without Authorization header should return 401")
    void testProtectedPath_MissingAuthHeader_Returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users/me").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("filter() - Valid RS256 token should succeed and inject X-User-Id header")
    void testProtectedPath_ValidRs256Token_InjectsHeaders() {
        String userId = UUID.randomUUID().toString();
        String jti = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .subject("alice@example.com")
                .claims(Map.of("userId", userId, "jti", jti, "roles", List.of("ROLE_USER")))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        // Mock Redis: JTI is NOT in blocklist
        when(redisTemplate.hasKey("blocklist:jti:" + jti)).thenReturn(Mono.just(false));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(any())).thenAnswer(invocation -> {
            org.springframework.web.server.ServerWebExchange mutatedExchange = invocation.getArgument(0);
            assertThat(mutatedExchange.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo(userId);
            assertThat(mutatedExchange.getRequest().getHeaders().getFirst("X-User-Email")).isEqualTo("alice@example.com");
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, times(1)).filter(any());
    }

    @Test
    @DisplayName("filter() - Revoked JTI in Redis blocklist should return 401")
    void testProtectedPath_RevokedJtiInRedis_Returns401() {
        String userId = UUID.randomUUID().toString();
        String jti = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .subject("banned@example.com")
                .claims(Map.of("userId", userId, "jti", jti, "roles", List.of("ROLE_USER")))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        // Mock Redis: JTI IS in blocklist
        when(redisTemplate.hasKey("blocklist:jti:" + jti)).thenReturn(Mono.just(true));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("filter() - Tampered token signature should return 401")
    void testProtectedPath_TamperedSignature_Returns401() {
        // Generate token signed with a DIFFERENT private key
        KeyPair otherKeyPair;
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            otherKeyPair = kpg.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String rogueToken = Jwts.builder()
                .subject("hacker@example.com")
                .claims(Map.of("userId", "hacker-123"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(otherKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + rogueToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }
}
