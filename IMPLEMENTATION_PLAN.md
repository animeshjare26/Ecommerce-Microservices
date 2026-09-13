# Master Implementation Plan & Learning Blueprint: E-Commerce Microservices

Welcome to the **E-Commerce Microservices Mastery Repository**. This document serves as the permanent, single source of truth for the entire architectural vision, phased roadmap, development rules, file reading order, and interview preparation guides for this project.

---

## 1. Project Vision & Development Standards

This repository is built not just as a portfolio piece, but as an **interactive interview-ready textbook in code form**. 

### 1.1 The Pedagogical Code Standards (Target for Every File)
The project aims for the following four learning standards. Foundational classes are documented in depth; smaller DTO, enum, and exception classes may currently have concise headers and will be expanded over time:
1. **File Purpose & Architectural Concept Header:**
   - At the top of every file, state:
     - **File Purpose:** What this file does in the microservice.
     - **Design Pattern / Spring Mechanism:** (e.g., *Spring Filter Chain, Repository Pattern, Strategy Pattern, Circuit Breaker*).
     - **Execution Flow Position:** Exactly where this file sits in the request lifecycle (e.g., *Step 3: Triggered after the JWT Filter validates the header and before the Service layer executes business logic*).
     - **Reading Order:** Which file to inspect before this, and which file to inspect next.
2. **Deep In-Line Explanations:**
   - Every critical line, annotation (e.g., `@Version`, `@Transactional(isolation = ...)`, `@Cacheable`, `@KafkaListener`), and configuration property must have explanatory comments answering **WHAT** it does and **WHY** it was chosen over alternatives.
3. **Tricky Interview Q&A Callouts:**
   - Embedded directly within class docstrings or section comments:
     - The common tricky interview questions asked by Tier-1 companies (Amazon, Uber, Microsoft, FinTechs) related to that exact file or concept.
     - The senior-level answer and edge cases.
4. **Zero Magic:**
   - No boilerplate without explanation. Even standard Spring Boot annotations will be dissected so you understand their internal proxy/reflection mechanics.

---

## 2. High-Level Architecture Overview

```
                                  +-----------------------+
                                  |     React Web App /   |
                                  |     Postman Client    |
                                  +-----------+-----------+
                                              |
                                              | HTTP (Port 8080)
                                              v
+-----------------------------------------------------------------------------------------+
|                               SPRING CLOUD API GATEWAY                                  |
|  - Cryptographic JWT Signature & Expiry Verification (HMAC-SHA256 / RS256)              |
|  - Token Revocation Check (Redis Blocklist)                                             |
|  - Token Claims Extraction (userId, email, roles)                                       |
|  - Downstream Header Injection: (X-User-Id: 42, X-User-Roles: ROLE_USER)                 |
|  - Redis Token-Bucket Rate Limiting                                                     |
|  - Distributed Tracing Correlation ID Injection (X-Correlation-Id)                      |
+-----------------------------------------------------------------------------------------+
       |                         |                       |                      |
       | HTTP (X-User-Id)        | HTTP (X-User-Id)      | HTTP (X-User-Id)     | HTTP
       v                         v                       v                      v
+--------------+          +--------------+        +--------------+       +--------------+
|   IDENTITY   |          |   PRODUCT    |        |     CART     |       |    ORDER     |
|   SERVICE    |          |   CATALOG    |        |   SERVICE    |       |   SERVICE    |
| (Port 8081)  |          | (Port 8082)  |        | (Port 8084)  |       | (Port 8085)  |
| PostgreSQL:  |          | PostgreSQL:  |        | Redis Store: |       | PostgreSQL:  |
|  identity_db |          |  product_db  |        |  cart:{uid}  |       |   order_db   |
+--------------+          +-------+------+        +--------------+       +-------+------+
                                  ^                                              |
                                  | Sync OpenFeign (Price Lock)                  |
                                  +----------------------------------------------+
                                                                                 |
=================================================================================| Kafka Events
|                                                                                v
|                      APACHE KAFKA EVENT BACKBONE                               |
|   Topics: order-events | inventory-events | payment-events | user-events       |
==================================================================================
         |                                |                               |
         | Consumes: OrderCreated         | Consumes: InventoryReserved   | Consumes All
         v                                v                               v
+-------------------+            +-------------------+           +-------------------+
| INVENTORY SERVICE |            |  PAYMENT SERVICE  |           |   NOTIFICATION    |
|   (Port 8083)     |            |   (Port 8086)     |           |     SERVICE       |
| PostgreSQL:       |            | PostgreSQL:       |           |   (Port 8087)     |
|   inventory_db    |            |   payment_db      |           | PostgreSQL:       |
| Optimistic Locks  |            | Idempotent Engine |           |  notification_db  |
+-------------------+            +-------------------+           +-------------------+
```

---

## 3. Microservice Domain Directory & Database Ownership

| Microservice | Port | Primary Storage | Key Responsibilities | Key Events Handled |
|---|---|---|---|---|
| **`identity-service`** | `8081` | PostgreSQL (`identity_db`) | User Registration, Password Hashing (`BCrypt`), JWT Access & Refresh Token issuance. Profile/address management is planned, not yet implemented. | `UserRegisteredEvent` publication is planned, not yet implemented.<br/>Consumes: None |
| **`product-service`** | `8082` | PostgreSQL (`product_db`) + Redis | Product catalog, Category hierarchy, SKU master data, Redis read-through caching | Publishes: `ProductPriceUpdatedEvent`<br/>Consumes: None |
| **`inventory-service`** | `8083` | PostgreSQL (`inventory_db`) | Stock tracking, atomic reservations, optimistic concurrency control (`@Version`) | Publishes: `InventoryReservedEvent`, `InventoryReservationFailedEvent`<br/>Consumes: `OrderCreatedEvent`, `PaymentFailedEvent` |
| **`cart-service`** | `8084` | Redis (In-Memory Key-Value) | Ephemeral shopping carts (`cart:{userId}`), fast session updates with TTL | Publishes: None<br/>Consumes: `OrderConfirmedEvent` (clears cart) |
| **`order-service`** | `8085` | PostgreSQL (`order_db`) | Order lifecycle state machine, Transactional Outbox, Choreographed Saga driver | Publishes: `OrderCreatedEvent`, `OrderConfirmedEvent`, `OrderCancelledEvent`<br/>Consumes: `InventoryReservedEvent`, `PaymentSuccessEvent`, `PaymentFailedEvent` |
| **`payment-service`** | `8086` | PostgreSQL (`payment_db`) | Mock gateway integration, idempotent charge execution, refund compensation | Publishes: `PaymentSuccessEvent`, `PaymentFailedEvent`<br/>Consumes: `InventoryReservedEvent` |
| **`notification-service`**| `8087` | PostgreSQL (`notification_db`)| Async welcome emails, order invoices, payment failure alerts | Publishes: None<br/>Consumes: All business lifecycle events |
| **`api-gateway`** | `8080` | Stateless (Redis for rate limiting) | Central ingress, JWT verification, Redis token-bucket rate limiter, header injection | None |
| **`discovery-server`** | `8761` | In-Memory Registry | Netflix Eureka Service Registry and dynamic heartbeat tracking | None |

---

## 4. Phased Step-by-Step Implementation Roadmap

### Phase 1: Core Foundation & Modular Domain (`identity-service` & `product-service`)
- [x] **Step 1.1:** Initialize the root infrastructure and multi-module configurations.
- [x] **Step 1.2:** Configure `docker-compose.yml` defining PostgreSQL 16 (initializing isolated databases) and Redis 7.
- [x] **Step 1.3:** Build `user-service` from scratch:
  - Domain Entities: `User`, `Role`, `RefreshToken`.
  - Flyway Migrations: `V1__init_user_schema.sql` with indexes and unique constraints.
  - Security Core: `BCryptPasswordEncoder(12)`, `JwtUtils` (modern JJWT 0.12.6), `UserDetailsServiceImpl`.
  - REST Layer: `/auth/signup`, `/auth/login`, `/auth/refresh`, `/auth/email-exists`, `/auth/forgot-password`, `/auth/reset-password`, `/users/me`.
  - Response Envelope: `GenericResponse<T>` (`{ success, message, data }`).
  - Global Error Handling: `@RestControllerAdvice` mapping validation and security errors to `GenericResponse.error()`.
  - Comprehensive in-line comments, file reading order, and tricky interview Q&A callouts in every file.
- [x] **Step 1.4:** Build `product-service`:
  - Domain Entities: `Product`, `Category`, `StockStatus` enum (`IN_STOCK`, `LOW_STOCK`, `OUT_OF_STOCK`).
  - Standardized Data Types: `Long` for `stockQuantity`, `lowStockThreshold`, `sellerId`, and `BigDecimal` for `price`.
  - Flyway Migrations: `V1__init_product_schema.sql` with composite B-Tree indexes and seed catalog items.
  - Performance: Spring Data JPA pagination with `@EntityGraph(attributePaths = {"category"})` eliminating N+1 query latency in 1 SQL query.
  - Caching Layer: Redis read-through caching (`@Cacheable`, `@CacheEvict`) with Jackson JSON serialization and tiered TTLs.
  - Event Dampened Stock Thresholds: `getAll` returns coarse `StockStatus` (maximizing cache stability); `getById` surfaces urgency messages when in `LOW_STOCK`.
  - Edge & Downstream Security: Public catalog browsing; RBAC enforcement for mutations via `RoleAuthorizationInterceptor` (`ROLE_ADMIN` / `ROLE_SELLER`).
  - API Gateway Routes: Registered `/api/v1/products/**` and `/api/v1/categories/**` routing to `lb://product-service`.
  - Pedagogical Standards & Tests: 12 comprehensive unit and slice tests, and `PRODUCT_SERVICE_MASTER_GUIDE.md`.

### Phase 2: Edge Routing, Discovery, Cart & Synchronous Resilience
- [x] **Step 2.1:** Create `discovery-server` with Spring Cloud Netflix Eureka.
- [x] **Step 2.2:** Create `api-gateway` with Spring Cloud Gateway:
  - Custom Reactive Global Filter: Cryptographic RS256 signature and expiration verification using RSA 2048-bit public key.
  - Revocation: Redis-backed Token JTI blocklist check (`blocklist:jti:{jti}`).
  - Header Propagation: Injects `X-User-Id`, `X-User-Email`, and `X-User-Roles` into downstream requests.
  - Rate Limiting: Redis-backed Token Bucket filter (`KeyResolver` by user ID and client IP).
  - Distributed Tracing: Injects and propagates `X-Correlation-Id` across request and response headers.
- [ ] **Step 2.3:** Build `cart-service`:
  - Redis primary storage for carts with TTL.
  - OpenFeign Client calling `product-service` with Resilience4j Circuit Breaker and Fallback.
- [ ] **Step 2.4:** Build `inventory-service`:
  - Concurrency & Stock: Optimistic locking with `@Version` and row-level atomic SQL updates (`WHERE available >= qty`).

### Phase 3: Event-Driven Architecture & Choreographed Saga
- [ ] **Step 3.1:** Add Apache Kafka broker to `docker-compose.yml`.
- [ ] **Step 3.2:** Build `order-service`:
  - Order state machine (`PENDING`, `CONFIRMED`, `CANCELLED`).
  - **Transactional Outbox Pattern:** Table `outbox_events` written in the same DB transaction as the Order.
  - Scheduled relay publisher sending events to Kafka topic `order-events`.
- [ ] **Step 3.3:** Build `payment-service`:
  - Kafka Consumer listening to `InventoryReservedEvent`.
  - Idempotency engine using `payment_idempotency_keys` table.
  - Publishes `PaymentSuccessEvent` or `PaymentFailedEvent`.
- [ ] **Step 3.4:** Implement Saga Failure Compensations:
  - Out of stock $\rightarrow$ Order cancelled.
  - Payment failed $\rightarrow$ Inventory released + Order cancelled + Email sent.
- [ ] **Step 3.5:** Build `notification-service`:
  - Kafka consumers for all topics rendering templated logs/emails.

### Phase 4: Production Observability, Tracing & Metrics
- [ ] **Step 4.1:** Add Zipkin, Prometheus, and Grafana to `docker-compose.yml`.
- [ ] **Step 4.2:** Integrate Spring Boot Actuator and Micrometer Tracing across all services.
- [ ] **Step 4.3:** Configure SLF4J MDC logging: Propagate `[traceId, spanId, X-User-Id, X-Correlation-Id]` across all log outputs.
- [ ] **Step 4.4:** Build a live Grafana dashboard tracking latency percentiles (p95, p99), error rates, and connection pool utilization.

### Phase 5: Testing Masterclass & Interview Polish
- [ ] **Step 5.1:** Unit testing suite with JUnit 5, Mockito, and AssertJ.
- [ ] **Step 5.2:** Integration testing using **Testcontainers** (spinning up real Postgres and Kafka containers during test execution).
- [ ] **Step 5.3:** WireMock tests for OpenFeign HTTP client failures.
- [ ] **Step 5.4:** Complete Interview Masterclass Q&A compendium and system design pitch script.

---

## 5. Architectural Guide: How to Read the Codebase (The "Start File" Map)

When you or an interviewer explore a microservice in this project, follow this exact mental path:

```text
1. START HERE: pom.xml / build.gradle
   └── Purpose: Understand what dependencies and capabilities this service has (JPA, Kafka, Security, Redis).

2. NEXT: resources/application.yml & db/migration/V1__init_*.sql
   └── Purpose: Understand configuration, external connection points, and the database schema/tables.

3. NEXT: entity/
   └── Purpose: Understand the domain model and relationships (OneToMany, ManyToOne, versioning).

4. NEXT: repository/
   └── Purpose: Understand how queries are formed, custom JPQL, locking annotations, and index usages.

5. NEXT: service/ & service/impl/
   └── Purpose: Core business logic, transaction boundaries (@Transactional), and business exceptions.

6. NEXT: controller/ & dto/
   └── Purpose: REST API contract, request validation (@Valid, @NotNull), and HTTP status codes.

7. NEXT: event/ (producer & consumer)
   └── Purpose: Asynchronous event contracts, Kafka serialization, consumer groups, and idempotency.

8. NEXT: config/ & exception/
   └── Purpose: Cross-cutting concerns, global error handlers, security filter chains, and beans.
```

---

## 6. Local Development Prerequisite Checklist
Before starting Phase 1, ensure you have installed:
- **Java Development Kit (JDK):** Version 21 (Temurin / Oracle / Corretto).
- **Maven:** Version 3.9+ or use the Maven Wrapper (`mvnw`).
- **Docker Desktop:** Running on Windows with WSL2 backend.
- **Git:** Initialized in this workspace.

---

*This blueprint will be updated as each phase is completed to reflect delivered milestones.*
