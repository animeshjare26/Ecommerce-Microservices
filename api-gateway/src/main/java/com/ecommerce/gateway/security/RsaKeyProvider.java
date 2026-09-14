package com.ecommerce.gateway.security;

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
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * =====================================================================================
 * FILE: RsaKeyProvider.java
 * MODULE: api-gateway (Security Core)
 *
 * WHAT DOES THIS CLASS DO AND WHY DO WE NEED IT?
 * -------------------------------------------------------------------------------------
 * Loads our RSA 2048-bit Public Key from `certs/public_key.pem` on application startup
 * and caches it in memory as a Java `PublicKey` object.
 *
 * WHY DOES THE GATEWAY ONLY HAVE THE PUBLIC KEY?
 * -------------------------------------------------------------------------------------
 * We use asymmetric cryptography (RS256):
 * - `user-service` holds the PRIVATE key to SIGN tokens when users log in.
 * - `api-gateway` holds only the PUBLIC key to VERIFY signatures.
 *
 * Even if an attacker were to breach the Gateway, they CANNOT forge or create fake JWTs
 * because they only have the public key!
 *
 * HOW DOES IT WORK?
 * -------------------------------------------------------------------------------------
 * 1. Reads `certs/public_key.pem` on startup via `@PostConstruct`.
 * 2. Strips headers (`-----BEGIN PUBLIC KEY-----`, `-----END PUBLIC KEY-----`) and whitespace.
 * 3. Base64-decodes the remaining key string into raw bytes.
 * 4. Passes bytes into Java's `KeyFactory.getInstance("RSA").generatePublic(...)`.
 * 5. Supplies the parsed key to `JwtAuthenticationFilter` for fast (<0.1ms) token verification.
 *
 * READING ORDER:
 * - Read PREVIOUS: resources/certs/public_key.pem
 * - Read THIS FILE: Understand X.509 PEM decoding into Java PublicKey.
 * - Read NEXT: filter/JwtAuthenticationFilter.java
 *
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Senior Question: What would happen if an attacker gained full root access to the Gateway filesystem?
 * A1: Because the Gateway operates under the **Zero-Trust Principle of Least Privilege**, it holds
 *     ONLY `public_key.pem`. Even with full root access to the Gateway server, the attacker has
 *     ZERO ability to sign or forge new tokens! To forge tokens, they would need the Private Key,
 *     which lives strictly inside `user-service` behind internal VPC security groups.
 *
 * Q2: Intermediate Question: What is ASN.1 and DER encoding in the context of X.509 keys?
 * A2: RSA keys in PEM files are actually ASN.1 (Abstract Syntax Notation One) structures
 *     encoded in binary DER (Distinguished Encoding Rules) format, and then Base64 encoded
 *     between the header `-----BEGIN PUBLIC KEY-----` and footer `-----END PUBLIC KEY-----`.
 *     Java's `X509EncodedKeySpec` natively parses this DER byte array.
 *
 * Q3: Beginner Intern Question: Why do we clean the PEM string using regex `\\s+`?
 * A3: Different operating systems format PEM line endings differently (Windows uses `\r\n`,
 *     Linux uses `\n`). Stripping all whitespace, newlines, and carriage returns ensures
 *     `Base64.getDecoder().decode()` receives a pure alphanumeric string regardless of OS!
 * =====================================================================================
 */
@Component
@Slf4j
public class RsaKeyProvider {

    private final ResourceLoader resourceLoader;

    @Value("${jwt.rsa.public-key-path}")
    private String publicKeyPath;

    @Getter
    private PublicKey publicKey;

    public RsaKeyProvider(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void loadPublicKey() {
        try {
            log.info("Loading RSA Public Key from path: {}", publicKeyPath);

            Resource resource = resourceLoader.getResource(publicKeyPath);
            String rawPem;
            try (InputStream is = resource.getInputStream()) {
                rawPem = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            // Strip PEM headers, footers, and whitespace
            String cleanPem = rawPem.replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "")
                    .trim();

            byte[] keyBytes = Base64.getDecoder().decode(cleanPem);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            this.publicKey = keyFactory.generatePublic(keySpec);

            log.info("RSA Public Key successfully initialized. Algorithm: {}, Format: {}",
                    this.publicKey.getAlgorithm(), this.publicKey.getFormat());
        } catch (Exception e) {
            log.error("CRITICAL: Failed to load RSA Public Key from path: {}", publicKeyPath, e);
            throw new IllegalStateException("Could not initialize RSA Public Key for API Gateway", e);
        }
    }
}
