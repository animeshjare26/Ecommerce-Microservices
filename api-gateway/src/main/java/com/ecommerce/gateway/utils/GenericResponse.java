package com.ecommerce.gateway.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * =====================================================================================
 * FILE: GenericResponse.java
 * MODULE: api-gateway (Shared DTO / Envelope Pattern)
 * PURPOSE: Standardizes all HTTP responses from the API Gateway (success, errors, 
 *          authentication failures, rate limit exceedances) into an identical JSON schema.
 *
 * DESIGN PATTERN: Envelope Pattern (Uniform API Response Wrapper).
 *
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Why is an API Gateway Envelope critical in a microservices ecosystem?
 * A1: Without a standard gateway envelope, clients must write multiple error parsers:
 *     one for Gateway 401s, one for Gateway 429 rate limits, and one for backend 500s.
 *     Having the Gateway emit the exact same envelope schema `{ success, message, data, errors, timestamp }`
 *     ensures a seamless contract for frontend React / iOS developers.
 *
 * Q2: Beginner Intern Question: What does `@JsonInclude(JsonInclude.Include.NON_NULL)` do?
 * A2: It tells Jackson to omit fields from the final JSON payload if their Java value is null.
 *     For example, on a successful 200 response, `errors` is null, so `"errors": null`
 *     is not transmitted over the wire, saving bandwidth!
 * =====================================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenericResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private List<String> errors;
    
    @Builder.Default
    private OffsetDateTime timestamp = OffsetDateTime.now();

    public static <T> GenericResponse<T> success(T data, String message) {
        return GenericResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(OffsetDateTime.now())
                .build();
    }

    public static <T> GenericResponse<T> error(String message, List<String> errors) {
        return GenericResponse.<T>builder()
                .success(false)
                .message(message)
                .errors(errors)
                .timestamp(OffsetDateTime.now())
                .build();
    }

    public static <T> GenericResponse<T> error(String message) {
        return error(message, null);
    }
}
