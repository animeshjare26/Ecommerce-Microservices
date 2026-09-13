package com.ecommerce.user.security.jwt;

import com.ecommerce.user.config.JwtConfig;
import com.ecommerce.user.enums.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

/**
 * =====================================================================================
 * FILE: JwtUtils.java
 * MODULE: user-service
 * PURPOSE: Cryptographic engine for creating, signing, parsing, and validating JWT tokens
 *          using the modern JJWT 0.12.6 API.
 * 
 * DESIGN PATTERN: Utility Bean (Stateless Component).
 * 
 * HOW JWT TOKENS WORK UNDER THE HOOD:
 * A JSON Web Token consists of three parts separated by dots (`.`):
 *   HEADER . PAYLOAD . SIGNATURE
 * 
 * 1. HEADER (Base64Url-encoded):
 *    { "alg": "HS256", "typ": "JWT" }
 * 
 * 2. PAYLOAD / CLAIMS (Base64Url-encoded JSON):
 *    {
 *      "sub": "user@example.com",        // Subject: user identifier
 *      "iat": 1726214400,                // Issued At timestamp
 *      "exp": 1726215300,                // Expiration timestamp
 *      "id": "c1f7a2d4-...",             // JTI: unique token ID for revocation
 *      "roles": ["ROLE_USER"]            // Custom claims (roles, permissions)
 *    }
 * 
 * 3. SIGNATURE:
 *    Calculated as: HMACSHA256(Base64Url(Header) + "." + Base64Url(Payload), secretKey)
 *    Because the secret key is known ONLY to the server, nobody can change the payload 
 *    (e.g., tamper with their user ID or role) without causing the signature verification to FAIL!
 * =====================================================================================
 */
@Component
@Slf4j
public class JwtUtils {

    // Injected type-safe configuration containing secrets and expiration minutes for each token type
    private final JwtConfig jwtConfig;

    public JwtUtils(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    /**
     * Generates a signed, compacted JWT string.
     * 
     * @param tokenType ACCESS_TOKEN, REFRESH_TOKEN, or FORGOT_PASSWORD
     * @param subject typically the user's unique email address
     * @param claims custom key-value pairs (e.g., token UUID 'id', user roles)
     * @return the serialized JWT string
     */
    public String generateToken(TokenType tokenType, String subject, Map<String, ?> claims) {
        // Step 1: Look up the configuration (secret + expiration) for this specific token type
        JwtConfig.TokenConfig tokenConfig = jwtConfig.getTokenConfigByType(tokenType);
        
        // Step 2: Convert expiration from minutes to milliseconds
        long expirationMillis = tokenConfig.getExpiration() * 60 * 1000;
        
        // Step 3: Compute current time and future expiration date
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMillis);

        // Step 4: Use JJWT 0.12.6 fluent builder to assemble the token
        return Jwts.builder()
                // Sets the 'sub' (subject) claim
                .subject(subject)
                // Adds our custom key-value map into the token payload
                .claims(claims)
                // Sets the 'iat' (issued at) claim to the current moment
                .issuedAt(now)
                // Sets the 'exp' (expiration) claim
                .expiration(expiryDate)
                // Cryptographically signs the header + payload using HMAC-SHA256 and our secret key
                .signWith(getKey(tokenConfig.getSecret()), Jwts.SIG.HS256)
                // Serializes everything into the final compacted URL-safe string format (xxx.yyy.zzz)
                .compact();
    }

    /**
     * Converts a Base64-encoded secret string into a cryptographic HMAC-SHA SecretKey.
     * 
     * @param base64Secret the Base64 string from application.yml
     * @return the java.security SecretKey object
     */
    private SecretKey getKey(String base64Secret) {
        // 1. Decode the Base64 string into raw binary bytes
        byte[] keyBytes = Decoders.BASE64.decode(base64Secret);
        // 2. Wrap the bytes into a secure HMAC-SHA key representation
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Parses and cryptographically verifies an incoming token.
     * Throws JwtException if the signature is invalid, token is tampered, or token has expired.
     * 
     * @param tokenType the expected token scope
     * @param token the raw JWT string from the request header
     * @return the Claims payload
     */
    public Claims getAllClaimsFromToken(TokenType tokenType, String token) {
        // Step 1: Get the corresponding secret for this token type
        JwtConfig.TokenConfig tokenConfig = jwtConfig.getTokenConfigByType(tokenType);

        // Step 2: Build the JJWT parser with the verification secret key
        return Jwts.parser()
                // Verify the HMAC signature using our secret key
                .verifyWith(getKey(tokenConfig.getSecret()))
                // Build the parser instance
                .build()
                // Parse the signed claims (throws ExpiredJwtException, SignatureException if invalid)
                .parseSignedClaims(token)
                // Return the claims payload body
                .getPayload();
    }

    /**
     * Validates whether a token is valid, un-tampered, and currently active.
     * 
     * @return true if token is valid, false if signature check fails or token is expired
     */
    public boolean validateToken(TokenType tokenType, String token) {
        try {
            // Attempt to parse claims. If valid, no exception is thrown
            getAllClaimsFromToken(tokenType, token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // Catches ExpiredJwtException, MalformedJwtException, SignatureException
            log.warn("Invalid JWT token [{}]: {}", tokenType, e.getMessage());
            return false;
        }
    }

    /**
     * Helper to extract the token subject (email).
     */
    public String getSubject(TokenType tokenType, String token) {
        return getAllClaimsFromToken(tokenType, token).getSubject();
    }

    /**
     * Helper to extract standard claims map (subject and token UUID).
     */
    public Map<String, String> getClaimsFromToken(TokenType tokenType, String token) {
        Claims claims = getAllClaimsFromToken(tokenType, token);
        return Map.of(
                "subject", claims.getSubject(),
                "id", claims.get("id") != null ? claims.get("id").toString() : ""
        );
    }
}
