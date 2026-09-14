package com.ecommerce.user.security.jwt;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * =====================================================================================
 * FILE: user-service/.../security/jwt/RsaKeyProvider.java
 * MODULE: user-service (Security Core)
 *
 * WHAT DOES THIS CLASS DO AND WHY DO WE NEED IT?
 * -------------------------------------------------------------------------------------
 * Loads and parses our RSA 2048-bit Asymmetric Keys from PEM files on startup:
 * 1. PRIVATE KEY (`private_key.pem` in PKCS#8 format):
 *    Kept strictly secret inside `user-service`. Used by `JwtUtils` to cryptographically
 *    SIGN access tokens when users log in.
 *
 * 2. PUBLIC KEY (`public_key.pem` in X.509 format):
 *    Used to verify signatures. Also exposed via `/api/auth/public-key` for the API Gateway
 *    or verifiers if needed.
 *
 * WHY RS256 (ASYMMETRIC) OVER HS256 (SYMMETRIC)?
 * -------------------------------------------------------------------------------------
 * With symmetric HMAC (HS256), the same secret key is shared with everyone who verifies tokens.
 * If the Gateway or another service is compromised, attackers get the key and can forge admin tokens.
 *
 * With RS256, ONLY `user-service` has the Private Key to sign tokens. The Gateway only needs the
 * Public Key, so it can verify tokens without having any power to forge them.
 *
 * READING ORDER:
 * - Read PREVIOUS: JwtConfig.java
 * - Read THIS FILE: Understand RSA key loading (PKCS#8 vs X.509).
 * - Read NEXT: JwtUtils.java
 *
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: What is the technical difference between PKCS#8 and X.509 formats?
 * A1: - PKCS#8 (Private-Key Information Syntax Standard) is the standard format for
 *       storing PRIVATE keys (e.g., `-----BEGIN PRIVATE KEY-----`). In Java, it maps
 *       directly to `PKCS8EncodedKeySpec`.
 *     - X.509 (SubjectPublicKeyInfo) is the standard format for storing PUBLIC keys
 *       and certificates (e.g., `-----BEGIN PUBLIC KEY-----`). In Java, it maps
 *       directly to `X509EncodedKeySpec`.
 *
 * Q2: Why is RS256 preferred over HMAC-SHA256 in high-security microservice ecosystems?
 * A2: Zero-Trust Security! In HMAC-SHA256, the verification key is identical to the signing
 *     key. If an edge Gateway or third-party service holding the key is breached, an attacker
 *     can forge valid tokens for any administrator or user. With RS256, verifiers (like the
 *     API Gateway) hold ONLY the Public Key, which can verify signatures but has ZERO
 *     mathematical capability to forge tokens!
 *
 * Q3: What is the computational trade-off of RSA vs. Symmetric HMAC?
 * A3: RSA involves modular exponentiation on large prime numbers (2048 bits), consuming
 *     approximately 3x to 5x more CPU cycles than HMAC hashing. We mitigate this by using
 *     fast 2048-bit keys (optimal balance between 112 bits of cryptographic security and
 *     millisecond latency) and verifying tokens in-memory without network I/O.
 * =====================================================================================
 */
@Component
@Slf4j
public class RsaKeyProvider {

    private final ResourceLoader resourceLoader;

    @Value("${jwt.rsa.private-key-path:classpath:certs/private_key.pem}")
    private String privateKeyPath;

    @Value("${jwt.rsa.public-key-path:classpath:certs/public_key.pem}")
    private String publicKeyPath;

    @Getter
    private PrivateKey privateKey;

    @Getter
    private PublicKey publicKey;

    @Getter
    private String publicKeyPem;

    public RsaKeyProvider(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void loadKeys() {
        try {
            log.info("Loading RSA Asymmetric Keypair from paths: private={}, public={}",
                    privateKeyPath, publicKeyPath);

            // 1. Load and parse Private Key (PKCS#8)
            String rawPrivateKeyPem = readResourceContent(privateKeyPath);
            String cleanPrivateKey = cleanPem(rawPrivateKeyPem, "PRIVATE KEY");
            byte[] privateKeyBytes = Base64.getDecoder().decode(cleanPrivateKey);
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            this.privateKey = keyFactory.generatePrivate(privateKeySpec);

            // 2. Load and parse Public Key (X.509)
            this.publicKeyPem = readResourceContent(publicKeyPath);
            String cleanPublicKey = cleanPem(this.publicKeyPem, "PUBLIC KEY");
            byte[] publicKeyBytes = Base64.getDecoder().decode(cleanPublicKey);
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
            this.publicKey = keyFactory.generatePublic(publicKeySpec);

            log.info("RSA 2048-bit Keypair successfully initialized. Algorithm: {}, Format: {}",
                    this.privateKey.getAlgorithm(), this.privateKey.getFormat());
        } catch (Exception e) {
            log.error("CRITICAL: Failed to load RSA keys from PEM files! Path: private={}, public={}",
                    privateKeyPath, publicKeyPath, e);
            throw new IllegalStateException("Could not initialize RSA asymmetric cryptographic keys", e);
        }
    }

    /**
     * Reads the entire content of a Spring Resource (classpath: or file:) into a UTF-8 String.
     */
    private String readResourceContent(String path) throws Exception {
        Resource resource = resourceLoader.getResource(path);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Strips standard PEM headers, footers, whitespace, and line breaks to isolate
     * the pure Base64-encoded ASN.1 DER binary payload.
     */
    private String cleanPem(String pem, String type) {
        return pem.replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s+", "")
                .trim();
    }
}
