package com.ecommerce.user.controller;

import com.ecommerce.user.security.jwt.RsaKeyProvider;
import com.ecommerce.user.utils.GenericResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * =====================================================================================
 * FILE: PublicKeyController.java
 * MODULE: user-service (REST Controller)
 * PURPOSE: Exposes the RSA 2048-bit Public Key in standard PEM and JSON formats to allow
 *          edge API Gateways and downstream services to verify RS256 token signatures.
 *
 * DESIGN PATTERN: Public Key / JWKS Endpoint Pattern (Zero-Trust Key Distribution).
 *
 * EXECUTION FLOW POSITION:
 * - Step 1: Called at startup or on cache eviction by the API Gateway to retrieve
 *           the public key for token verification.
 * - Step 2: Public endpoint (whitelisted in SecurityConfiguration without JWT required).
 *
 * READING ORDER:
 * - Read PREVIOUS: RsaKeyProvider.java
 * - Read THIS FILE: Understand how public keys are safely distributed in microservices.
 * - Read NEXT: AuthController.java
 *
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Why is exposing the Public Key completely safe over the public Internet?
 * A1: In asymmetric cryptography (RSA / ECC), the Public Key contains only the modulus (n)
 *     and public exponent (e). It can ONLY be used to VERIFY signatures or ENCRYPT data.
 *     It is mathematically impossible to derive the Private Key from the Public Key without
 *     factoring a 617-digit semiprime number (which would take classical supercomputers
 *     millions of years).
 *
 * Q2: How do production systems (Google, Keycloak, Auth0) handle public key rotation?
 * A2: They implement JWKS (JSON Web Key Sets) at `/.well-known/jwks.json`. Each key has a
 *     `kid` (Key ID). When rotating keys, the server publishes both old and new public keys.
 *     Incoming JWTs have a `kid` header indicating which key signed them. The Gateway
 *     caches the JWKS and only fetches a new one if an unknown `kid` is encountered!
 *
 * Q3: Beginner Intern Question: Can a client use this public key to generate their own JWT?
 * A3: Absolutely NOT! Token creation requires SIGNING with the PRIVATE KEY. The public key
 *     can only be used to TEST if a signature was created by the matching private key.
 * =====================================================================================
 */
@RestController
@RequestMapping("/auth/public-key")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Public Key Discovery", description = "Public Key endpoint for asymmetric RS256 token verification")
public class PublicKeyController {

    private final RsaKeyProvider rsaKeyProvider;

    /**
     * Returns the RSA Public Key formatted as a standard PEM string.
     * Compatible with OpenSSL, Postman, and jwt.io for manual signature validation.
     */
    @Operation(summary = "Get RSA Public Key in PEM format", description = "Returns raw X.509 public key PEM for token signature verification")
    @GetMapping(value = "/pem", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getPublicKeyPem() {
        log.info("Public key PEM requested by verifier");
        return ResponseEntity.ok(rsaKeyProvider.getPublicKeyPem());
    }

    /**
     * Returns the RSA Public Key inside our standard GenericResponse JSON envelope.
     */
    @Operation(summary = "Get RSA Public Key in JSON envelope", description = "Returns public key algorithm, format, and PEM string")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericResponse<Map<String, String>>> getPublicKeyJson() {
        Map<String, String> keyData = Map.of(
                "algorithm", "RSA",
                "format", "X.509",
                "publicKey", rsaKeyProvider.getPublicKeyPem()
        );
        return ResponseEntity.ok(GenericResponse.success(keyData, "RSA Public Key retrieved successfully"));
    }
}
