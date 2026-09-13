package com.ecommerce.user.exception;

import com.ecommerce.user.utils.GenericResponse;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * =====================================================================================
 * FILE: GlobalExceptionHandler.java
 * MODULE: user-service
 * PURPOSE: Intercepts exceptions thrown by any Controller and formats them into GenericResponse.error().
 * 
 * DESIGN PATTERN: Exception Filter / Interceptor Pattern, Aspect-Oriented Programming (AOP).
 * 
 * EXECUTION FLOW:
 * 1. An exception occurs in Service or Controller layer.
 * 2. Uncaught exception bubbles up towards the Servlet container.
 * 3. DispatcherServlet consults HandlerExceptionResolver beans.
 * 4. This @RestControllerAdvice intercepts the exception and builds the formatted HTTP response.
 * 
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: What is the difference between `@ControllerAdvice` and `@RestControllerAdvice`?
 * A1: `@RestControllerAdvice` is a meta-annotation that combines `@ControllerAdvice` with 
 *     `@ResponseBody`. It guarantees that all handler method return values are serialized 
 *     directly to JSON by HttpMessageConverter instead of trying to resolve HTML view templates.
 * 
 * Q2: Can `@ExceptionHandler` intercept exceptions thrown inside Spring Security Filters?
 * A2: NO! Filter execution happens in the Servlet Filter pipeline BEFORE DispatcherServlet 
 *     is reached. To handle security filter errors (like 401 Unauthorized), we must configure 
 *     an `AuthenticationEntryPoint` in `SecurityFilterChain`!
 * =====================================================================================
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles custom business domain exceptions with HTTP 400 Bad Request.
     */
    @ExceptionHandler({DataNotFoundException.class, EntityExistsException.class, ValidationException.class})
    public ResponseEntity<GenericResponse<Object>> handleDomainExceptions(RuntimeException ex) {
        log.warn("Domain exception encountered: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(GenericResponse.error(null, ex.getMessage()));
    }

    /**
     * Handles Jakarta Bean Validation errors (@Valid on request DTOs) with HTTP 400.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GenericResponse<Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = (fieldError != null && fieldError.getDefaultMessage() != null)
                ? fieldError.getDefaultMessage()
                : "Validation error occurred";
        log.warn("Validation error on field: {}", message);
        return ResponseEntity.badRequest().body(GenericResponse.error(null, message));
    }

    /**
     * Handles invalid login credentials with HTTP 401 Unauthorized.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<GenericResponse<Object>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Authentication failed: Incorrect email or password");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(GenericResponse.error(null, "Incorrect email or password"));
    }

    /**
     * Handles custom unauthorized exceptions.
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<GenericResponse<Object>> handleUnauthorized(UnauthorizedException ex) {
        log.warn("Unauthorized access: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(GenericResponse.error(null, ex.getMessage()));
    }

    /**
     * Handles JWT token expiration, signature failures, or malformed tokens.
     */
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<GenericResponse<Object>> handleJwtException(JwtException ex) {
        log.warn("JWT validation error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(GenericResponse.error(null, "Invalid or expired authentication token"));
    }

    /**
     * Handles RBAC access denied exceptions with HTTP 403 Forbidden.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<GenericResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(GenericResponse.error(null, "Access denied: You do not have permission for this resource"));
    }

    /**
     * Fallback for any unhandled generic server exceptions with HTTP 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse<Object>> handleGenericException(Exception ex) {
        log.error("Unhandled internal server error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(GenericResponse.error(null, "An unexpected server error occurred. Please try again later."));
    }
}
