package com.ecommerce.product.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * =====================================================================================
 * FILE: product-service/.../utils/GenericResponse.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: Standard unified JSON response envelope for all REST API endpoints.
 *
 * DESIGN PATTERN / ARCHITECTURAL CONCEPT:
 * - Response Envelope Pattern: Ensures clients receive a predictable JSON structure
 *   consisting of { success, message, data, timestamp, errors }.
 *
 * READING ORDER:
 * - Read PREVIOUS: repository/ProductRepository.java
 * - Read THIS FILE: Understand the API payload contract.
 * - Read NEXT: config/RedisConfig.java
 *
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q: Why wrap API responses in a GenericResponse envelope rather than returning raw DTOs?
 * A: Returning raw DTOs creates contract fragmentation across microservices. A unified envelope
 *    provides standard metadata (success flag, human-readable message, ISO timestamp,
 *    and field-level error lists for validation failures) that client frontend apps (React, iOS)
 *    can handle with a single interceptor.
 * =====================================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenericResponse<T> {

    @Builder.Default
    private boolean success = true;

    private String message;

    private T data;

    @Builder.Default
    private Instant timestamp = Instant.now();

    private List<String> errors;

    public static <T> GenericResponse<T> success(T data, String message) {
        return GenericResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> GenericResponse<T> error(String message, List<String> errors) {
        return GenericResponse.<T>builder()
                .success(false)
                .message(message)
                .errors(errors)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> GenericResponse<T> error(String message) {
        return GenericResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }
}
