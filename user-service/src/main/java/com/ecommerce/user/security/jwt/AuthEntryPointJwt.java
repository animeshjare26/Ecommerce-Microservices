package com.ecommerce.user.security.jwt;

import com.ecommerce.user.utils.GenericResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * =====================================================================================
 * FILE: AuthEntryPointJwt.java
 * MODULE: user-service
 * PURPOSE: Handles unauthorized access attempts (HTTP 401) within the Spring Security Filter Chain.
 * 
 * DESIGN PATTERN: Strategy Pattern (AuthenticationEntryPoint implementation).
 * 
 * EXECUTION FLOW:
 * 1. An unauthenticated request tries to access an authenticated endpoint (e.g. /api/v1/users/me).
 * 2. FilterSecurityInterceptor / AuthorizationFilter detects absence of Authentication in SecurityContext.
 * 3. Throws AccessDeniedException or InsufficientAuthenticationException.
 * 4. ExceptionTranslationFilter catches it and delegates to this `commence()` method.
 * 5. Returns a structured JSON response instead of the default HTML error page.
 * 
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Why can't we catch unauthenticated requests in `@RestControllerAdvice`?
 * A1: The Spring Security Filter Chain executes BEFORE the `DispatcherServlet` is reached. 
 *     `@RestControllerAdvice` only catches exceptions thrown within Controller handler methods. 
 *     Security filter exceptions must be intercepted by an `AuthenticationEntryPoint`.
 * =====================================================================================
 */
@Component
@Slf4j
public class AuthEntryPointJwt implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        
        log.warn("Unauthorized request to [{} {}]: {}", request.getMethod(), request.getRequestURI(), authException.getMessage());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        GenericResponse<Object> errorResponse = GenericResponse.error(
                null, 
                "Unauthorized: Full authentication is required to access this resource (" + authException.getMessage() + ")"
        );

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
