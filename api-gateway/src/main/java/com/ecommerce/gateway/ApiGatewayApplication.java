package com.ecommerce.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * =====================================================================================
 * FILE: ApiGatewayApplication.java
 * MODULE: api-gateway (Spring Cloud Reactive API Gateway)
 * PURPOSE: Bootstrap entry point for the reactive Spring Cloud API Gateway.
 *
 * DESIGN PATTERN / SPRING MECHANISM:
 * - API Gateway Pattern (Central Public Ingress on Port 8080).
 * - Reactive Non-Blocking Netty Engine (Spring WebFlux).
 *
 * EXECUTION FLOW POSITION:
 * - Listens on Port 8080.
 * - Routes all external client traffic to internal microservices via Eureka (`lb://user-service`).
 * - Validates RS256 JWTs and injects `X-User-Id`, `X-User-Roles`, `X-Correlation-Id`.
 *
 * READING ORDER:
 * - Read PREVIOUS: exception/GatewayExceptionHandler.java
 * - Read THIS FILE: Understand how Spring Boot boots the reactive Netty gateway.
 * - Read NEXT: API_GATEWAY_MASTER_GUIDE.md
 *
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Senior Question: Why does Spring Cloud Gateway use Netty instead of Tomcat?
 * A1: Tomcat creates 1 OS thread per concurrent connection. If 1,000 clients hold connections
 *     open, Tomcat consumes ~1GB of RAM just for thread call stacks, and context-switching
 *     degrades CPU performance. Netty runs an EventLoop model (typically 2 * CPU cores threads).
 *     Connections are managed as lightweight non-blocking channel selectors, allowing a single
 *     server to easily sustain 100,000 concurrent streaming connections with 50MB of RAM!
 *
 * Q2: Intermediate Question: How does Spring Cloud Gateway discover Eureka services?
 * A2: It registers as a Eureka client (`spring-cloud-starter-netflix-eureka-client`).
 *     At bootup, it contacts `http://localhost:8761/eureka/`, downloads the instance list,
 *     and registers its own instance so other services and monitoring dashboards can see it.
 *
 * Q3: Beginner Intern Question: Can we run business logic (e.g. database JPA queries) inside the Gateway?
 * A3: ARCHITECTURAL ANTI-PATTERN! Never put database access or heavy business logic in the API Gateway.
 *     The Gateway must remain a stateless, ultra-fast routing, rate-limiting, and security shield.
 *     Any slow database query in the Gateway would block Netty event loops and slow down EVERY
 *     route on the entire platform!
 * =====================================================================================
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
