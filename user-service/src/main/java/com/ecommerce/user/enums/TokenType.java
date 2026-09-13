package com.ecommerce.user.enums;

/**
 * =====================================================================================
 * FILE: TokenType.java
 * MODULE: user-service
 * PURPOSE: Enumerates supported JSON Web Token types.
 * 
 * DESIGN PATTERN: Type-Safe Enum Pattern.
 * 
 * READING ORDER:
 * - Read PREVIOUS: application.yml
 * - Read THIS FILE: Understand distinct token scopes.
 * - Read NEXT: JwtConfig.java, JwtUtils.java
 * 
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Why do we use separate token types (ACCESS vs REFRESH) instead of a single long-lived token?
 * A1: Security Defense-in-Depth. Access tokens are transmitted frequently over HTTP headers 
 *     and could potentially be intercepted; having a short TTL (15 mins) minimizes exposure. 
 *     Refresh tokens are used only occasionally to obtain new access tokens, can be stored 
 *     securely (e.g., httpOnly cookie or secure storage), and can be revoked server-side.
 * =====================================================================================
 */
public enum TokenType {
    ACCESS_TOKEN,
    REFRESH_TOKEN,
    FORGOT_PASSWORD
}
