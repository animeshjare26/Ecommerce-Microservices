package com.ecommerce.user.security.jwt;

import com.ecommerce.user.enums.TokenType;
import com.ecommerce.user.security.services.UserDetailsImpl;
import com.ecommerce.user.security.services.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * =====================================================================================
 * FILE: AuthTokenFilter.java
 * MODULE: user-service
 * PURPOSE: Intercepts every incoming HTTP request, extracts the JWT from the Authorization 
 *          header, cryptographically validates it, and populates Spring's SecurityContext.
 * 
 * DESIGN PATTERN: Interceptor / Filter Pattern (`OncePerRequestFilter`).
 * 
 * EXECUTION FLOW (LINE BY LINE):
 * 1. Request arrives at Tomcat container.
 * 2. Tomcat passes request into the Filter Chain pipeline.
 * 3. AuthTokenFilter.doFilterInternal() executes:
 *    - Reads the "Authorization" HTTP request header.
 *    - Checks if header starts with "Bearer ".
 *    - If NO: Request continues down filter chain (e.g. for public endpoints like /auth/login).
 *    - If YES: Substrings out the raw token string (skipping the 7 characters of "Bearer ").
 *    - Calls JwtUtils to cryptographically verify signature and check expiration.
 *    - If token is VALID:
 *      * Extracts user email (the subject).
 *      * Loads user details and roles via UserDetailsServiceImpl.
 *      * Creates an authenticated `UsernamePasswordAuthenticationToken`.
 *      * Injects this authentication object into `SecurityContextHolder.getContext()`.
 *      * Attaches user ID to HttpServletRequest attributes (`request.setAttribute("user-id", id)`).
 * 4. Calls `filterChain.doFilter(request, response)` to pass the request to the next filter.
 * 
 * READING ORDER:
 * - Read PREVIOUS: SecurityConfiguration.java
 * - Read THIS FILE: Understand how authentication context is established per request.
 * - Read NEXT: JwtUtils.java
 * 
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: How does `SecurityContextHolder` work across multiple concurrent threads?
 * A1: By default, `SecurityContextHolder` uses a `ThreadLocal` strategy (`MODE_THREADLOCAL`). 
 *     Each HTTP request dispatched to Tomcat runs on an independent worker thread. 
 *     When we store authentication in `SecurityContextHolder`, it is only visible to the current 
 *     request thread. When the request ends, Spring cleans up the ThreadLocal to prevent memory leaks 
 *     or thread pool data pollution!
 * 
 * Q2: Why delegate filter exceptions to `HandlerExceptionResolver`?
 * A2: Regular servlet filters run outside of Spring MVC's DispatcherServlet. If an exception 
 *     is thrown inside a filter, Spring's `@RestControllerAdvice` cannot catch it by default. 
 *     By autowiring `HandlerExceptionResolver` and calling `resolveException(request, response, null, e)`, 
 *     we route filter errors directly into our `GlobalExceptionHandler` so the client receives 
 *     our standard `GenericResponse.error()` JSON format!
 * =====================================================================================
 */
// Registers this class as a Spring-managed singleton component bean
@Component
// Lombok annotation providing an SLF4J logger instance (log.info, log.error)
@Slf4j
public class AuthTokenFilter extends OncePerRequestFilter {

    // Spring MVC's exception resolver bridge to route filter exceptions to @RestControllerAdvice
    @Autowired
    @Qualifier("handlerExceptionResolver")
    private HandlerExceptionResolver handlerExceptionResolver;

    // Cryptographic utility helper for verifying and parsing JWTs
    @Autowired
    private JwtUtils jwtUtils;

    // Service for loading user data from the database
    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    /**
     * Core filter method executed once for each incoming HTTP request.
     * 
     * @param request the incoming HTTP request
     * @param response the outgoing HTTP response
     * @param filterChain the remaining chain of filters to execute
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Step 1: Read the Authorization header from the incoming HTTP request
        // Standard format: "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
        String authHeader = request.getHeader("Authorization");

        // Step 2: Check if header is present and starts with standard Bearer schema
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // No Bearer token present. This is normal for public endpoints (like /auth/login or /auth/signup).
            // Pass the request along the filter chain. If this was a protected URL, 
            // the downstream AuthorizationFilter will detect the empty SecurityContext and reject it!
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Step 3: Extract the raw JWT string by removing the "Bearer " prefix (length = 7)
            String jwtToken = authHeader.substring(7);

            // Step 4: Cryptographically validate the token (checks HMAC signature and exp timestamp)
            if (jwtUtils.validateToken(TokenType.ACCESS_TOKEN, jwtToken)) {

                // Step 5: Extract the user email (subject claim) from the token payload
                String email = jwtUtils.getSubject(TokenType.ACCESS_TOKEN, jwtToken);

                // Step 6: Load user details and granted authorities (roles) from the database
                UserDetailsImpl userDetails = (UserDetailsImpl) userDetailsService.loadUserByUsername(email);

                // Step 7: Create a trusted UsernamePasswordAuthenticationToken
                // Notice the 3-argument constructor is used here:
                // (principal, credentials, authorities). This constructor explicitly marks 
                // the authentication object as `isAuthenticated() = true`!
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null, // Credentials (password) set to null because user is already authenticated via JWT
                                userDetails.getAuthorities() // User's assigned roles: [ROLE_USER, ROLE_ADMIN]
                        );

                // Step 8: Build and attach request details (IP address, Session ID) to the authentication object
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Step 9: CRITICAL! Store the authenticated token inside ThreadLocal SecurityContextHolder
                // Any downstream controller, service, or @PreAuthorize annotation can now see who the user is!
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // Step 10: Store user ID directly in request attributes for convenient controller retrieval
                request.setAttribute("user-id", userDetails.getId());
            }

            // Step 11: Pass the request down to the next filter in the chain (or to the Controller)
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("Authentication filter exception during token processing: {}", e.getMessage());
            // Step 12: Hand the exception to Spring's HandlerExceptionResolver so GlobalExceptionHandler formats it
            handlerExceptionResolver.resolveException(request, response, null, e);
        }
    }
}
