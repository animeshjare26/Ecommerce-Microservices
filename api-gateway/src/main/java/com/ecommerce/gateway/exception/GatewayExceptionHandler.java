package com.ecommerce.gateway.exception;

import com.ecommerce.gateway.utils.GenericResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * =====================================================================================
 * FILE: GatewayExceptionHandler.java
 * MODULE: api-gateway (Global Reactive Error Handler)
 * PURPOSE: Catches all unhandled exceptions and HTTP error codes at the Gateway edge
 *          (e.g., 404 Route Not Found, 429 Rate Limit Exceeded, 503 Downstream Down)
 *          and formats them into our unified `GenericResponse<T>` JSON envelope.
 *
 * DESIGN PATTERN / SPRING MECHANISM:
 * - Reactive Exception Translation Pattern (`ErrorWebExceptionHandler`).
 * - High Priority (`order = -2`): Intercepts errors before default Spring Boot error pages.
 *
 * READING ORDER:
 * - Read PREVIOUS: config/RateLimiterConfig.java
 * - Read THIS FILE: Understand reactive error handling in Spring WebFlux.
 * - Read NEXT: ApiGatewayApplication.java
 *
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Senior Question: Why can't we use `@RestControllerAdvice` in Spring Cloud Gateway?
 * A1: `@RestControllerAdvice` belongs to Spring MVC / WebFlux annotation-driven controllers.
 *     In Spring Cloud Gateway, requests do NOT terminate in a `@RestController`.
 *     They flow through reactive filter chains and Netty channel handlers.
 *     If a downstream microservice is down (503) or rate-limiting kicks in (429), the error
 *     is produced inside Netty's reactive pipeline where only an `ErrorWebExceptionHandler`
 *     can intercept and serialize it!
 *
 * Q2: Intermediate Question: What error does Gateway throw when Eureka has 0 instances of a service?
 * A2: It throws a `ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to find instance for user-service")`.
 *     Our handler catches this and translates it to a clean JSON response:
 *     `{ "success": false, "message": "Service temporarily unavailable. Please try again later." }`
 *
 * Q3: Beginner Intern Question: What happens if `response.isCommitted()` is true when an error occurs?
 * A3: If response headers or body chunks have already started streaming across the socket to the client,
 *     we CANNOT modify the status code or write a new JSON envelope! We must check `if (response.isCommitted())`
 *     and return `Mono.error(ex)` to prevent Netty buffer corruption.
 * =====================================================================================
 */
@Component
@Slf4j
public class GatewayExceptionHandler implements ErrorWebExceptionHandler, Ordered {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public int getOrder() {
        // High priority: execute before DefaultErrorWebExceptionHandler
        return -2;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        // If the socket has already started transmitting bytes, do not attempt to overwrite
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String errorMessage = "Internal server error occurred at API Gateway";

        // Map known Spring WebFlux and Gateway exceptions to appropriate HTTP status codes
        if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.resolve(rse.getStatusCode().value());
            if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
            errorMessage = rse.getReason() != null ? rse.getReason() : rse.getMessage();
        } else if (ex.getMessage() != null && ex.getMessage().contains("Unable to find instance")) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            errorMessage = "Downstream service is currently unavailable. Please try again in a few moments";
        } else if (ex.getMessage() != null && ex.getMessage().contains("Connection refused")) {
            status = HttpStatus.BAD_GATEWAY;
            errorMessage = "Failed to connect to downstream service (Connection refused)";
        }

        log.error("API Gateway error occurred on path [{}]: status={}, message={}",
                exchange.getRequest().getURI().getPath(), status, ex.getMessage());

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        GenericResponse<Void> errorEnvelope = GenericResponse.error(errorMessage, List.of(errorMessage));
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorEnvelope);
        } catch (JsonProcessingException e) {
            bytes = ("{\"success\":false,\"message\":\"" + errorMessage + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
