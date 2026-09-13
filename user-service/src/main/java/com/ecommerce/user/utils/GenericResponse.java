package com.ecommerce.user.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * =====================================================================================
 * FILE: GenericResponse.java
 * MODULE: user-service
 * PURPOSE: Standardized JSON envelope returned by all REST endpoints.
 * 
 * DESIGN PATTERN: Response Envelope Pattern, Builder Pattern.
 * 
 * EXECUTION FLOW:
 * - Every controller endpoint wraps its response payload inside GenericResponse.
 * - GlobalExceptionHandler wraps all error messages inside GenericResponse.error().
 * 
 * READING ORDER:
 * - Read THIS FILE: Understand the API payload contract.
 * - Read NEXT: GlobalExceptionHandler.java, AuthController.java
 * 
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Why should an enterprise REST API use a unified response wrapper like GenericResponse<T>?
 * A1: Predictability and client convenience. Frontend clients (React, Mobile) can write a 
 *     single Axios/Fetch interceptor checking `response.data.success`. If false, display 
 *     `response.data.message` in a toast notification. It also shields raw database error 
 *     structures and stack traces from leaking to the outside world.
 * =====================================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenericResponse<T> {

    // Indicates whether the business operation succeeded or failed
    private Boolean success;

    // Human-readable message (e.g. "User registered successfully", "Invalid password")
    private String message;

    // Generic generic data payload (User object, Token map, etc.)
    private T data;

    /**
     * Helper to create a successful response with default "success" message.
     */
    public static <T> GenericResponse<T> success(T data) {
        return GenericResponse.<T>builder()
                .success(true)
                .message("success")
                .data(data)
                .build();
    }

    /**
     * Helper to create a successful response with a customized business message.
     */
    public static <T> GenericResponse<T> success(T data, String message) {
        return GenericResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * Helper to create an error response with default "error" message.
     */
    public static <T> GenericResponse<T> error(T data) {
        return GenericResponse.<T>builder()
                .success(false)
                .message("error")
                .data(data)
                .build();
    }

    /**
     * Helper to create an error response with a descriptive error message.
     */
    public static <T> GenericResponse<T> error(T data, String message) {
        return GenericResponse.<T>builder()
                .success(false)
                .message(message)
                .data(data)
                .build();
    }
}
