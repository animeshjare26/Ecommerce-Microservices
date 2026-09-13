package com.ecommerce.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * =====================================================================================
 * FILE: RefreshToken.java
 * MODULE: user-service
 * PURPOSE: Stores active and revoked application-specific refresh-token identifiers for token rotation.
 * 
 * DESIGN PATTERN: Token Revocation / Whitelist Pattern.
 * 
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: What is "Refresh Token Rotation" and why is it critical?
 * A1: Every time a client sends a refresh token to get a new access token, the server 
 *     invalidates (revokes) the used refresh token and issues a completely new refresh token. 
 *     If an attacker reuses a refresh token after it was rotated, the server rejects that token.
 *     This implementation does not automatically revoke the user's other refresh tokens.
 * =====================================================================================
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // JWT ID (UUID) uniquely identifying this specific token instance
    @Column(name = "token_jti", nullable = false, unique = true, length = 100)
    private String tokenJti;

    @Column(name = "user_email", nullable = false, length = 150)
    private String userEmail;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "is_revoked", nullable = false)
    private Boolean isRevoked = Boolean.FALSE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    public RefreshToken(String tokenJti, String userEmail, OffsetDateTime expiresAt) {
        this.tokenJti = tokenJti;
        this.userEmail = userEmail;
        this.expiresAt = expiresAt;
        this.isRevoked = Boolean.FALSE;
    }
}
