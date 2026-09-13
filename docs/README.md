# E-Commerce Microservices Mastery: Architectural Knowledge Base & Interview Compendium

> **An interactive, pedagogical masterclass in cloud-native microservices engineering. Built to eliminate all "magic", bridge the gap between beginner intuition and staff-engineer depth, and prepare you for Tier-1 system design and backend coding interviews.**

---

## 🗺️ Master Curriculum & Reading Map

```text
                                  [ START HERE ]
                                         │
                                         ▼
                 ┌───────────────────────────────────────────────┐
                 │    01. JAVA FOUNDATIONS                      │
                 │  - OOP, SOLID, Collections, Concurrency      │
                 │  - JVM, GC, Streams, Virtual Threads         │
                 └───────────────────────┬───────────────────────┘
                                         │
                                         ▼
                 ┌───────────────────────────────────────────────┐
                 │    02. SPRING BOOT FOUNDATIONS                │
                 │  - IoC, MVC, Validation, AOP, Transactions    │
                 └───────────────────────┬───────────────────────┘
                                         │
                                         ▼
                 ┌───────────────────────────────────────────────┐
                 │    03. DATABASE & PERSISTENCE                 │
                 │  - SQL, PostgreSQL, JPA, Hibernate, Flyway    │
                 └───────────────────────┬───────────────────────┘
                                         │
                                         ▼
                 ┌───────────────────────────────────────────────┐
                 │    04. ARCHITECTURE FOUNDATIONS               │
                 │  - Monolith vs. Microservices vs. Distributed │
                 │  - Database-per-Service & Polyglot Storage    │
                 └───────────────────────┬───────────────────────┘
                                         │
                                         ▼
                 ┌───────────────────────────────────────────────┐
                 │    05. SECURITY & IDENTITY MASTERCLASS        │
                 │  - Stateful Sessions vs. Stateless JWTs       │
                 │  - JWT Anatomy & Cryptographic Math           │
                 │  - Symmetric HMAC vs. Asymmetric RS256        │
                 │  - Why We Need JTI (Single-Session Logout)    │
                 │  - Refresh Token Rotation & BCrypt Hashing    │
                 └───────────────────────┬───────────────────────┘
                                         │
                                         ▼
                 ┌───────────────────────────────────────────────┐
                 │    06. API DESIGN & RESILIENCE                │
                 │  - REST, Idempotency, Timeouts, Retries       │
                 │  - Circuit Breakers, Bulkheads, Backpressure  │
                 └───────────────────────┬───────────────────────┘
                                         │
                                         ▼
                 ┌───────────────────────────────────────────────┐
                 │    07. EDGE ROUTING & REACTIVE GATEWAY        │
                 │  - Netty Event Loops vs. Tomcat Threads       │
                 │  - Why Gateway Doesn't Call User Service      │
                 │  - Distributed Rate Limiting (Token Bucket)   │
                 │  - Centralized Edge CORS vs. Duplication Bug  │
                 │  - Distributed Tracing & Correlation IDs      │
                 └───────────────────────┬───────────────────────┘
                                         │
                                         ▼
                 ┌───────────────────────────────────────────────┐
                 │    08. SERVICE DISCOVERY & REGISTRY           │
                 │  - Client-Side vs. Server-Side Discovery      │
                 │  - Netflix Eureka & CAP Theorem (AP vs. CP)   │
                 │  - Self-Preservation Mode & Zombie Instances  │
                 └───────────────────────┬───────────────────────┘
                                         │
                                         ▼
                 ┌───────────────────────────────────────────────┐
                 │    09. KAFKA & EVENT-DRIVEN SYSTEMS           │
                 │  - Topics, Partitions, Consumer Groups, Lag   │
                 │  - Delivery Guarantees & Transactional Outbox │
                 │  - Saga Pattern (Orchestration vs Choreog.)   │
                 └───────────────────────┬───────────────────────┘
                                         │
                                         ▼
                 ┌───────────────────────────────────────────────┐
                 │    10. TESTING & QUALITY ASSURANCE            │
                 │  - Test Pyramid: Unit, Integration, E2E       │
                 │  - Testcontainers (Real PostgreSQL, Redis)    │
                 │  - Contract Testing (Spring Cloud Contract)   │
                 └───────────────────────┬───────────────────────┘
                                         │
                                         ▼
                 ┌───────────────────────────────────────────────┐
                 │    11. OBSERVABILITY & PRODUCTION             │
                 │  - 3 Pillars: Logs (MDC), Metrics, Traces     │
                 │  - OpenTelemetry, Zipkin, Prometheus, Grafana │
                 │  - Spring Actuator, Liveness & Readiness      │
                 └───────────────────────┬───────────────────────┘
                                         │
                                         ▼
                 ┌───────────────────────────────────────────────┐
                 │    12. DEPLOYMENT & CLOUD PLATFORMS           │
                 │  - Multi-stage Dockerfiles & Distroless JRE   │
                 │  - Kubernetes Deployments, Services, Config   │
                 │  - Zero-Downtime Deployments & CI/CD          │
                 └───────────────────────────────────────────────┘
```

---

## 📑 Syllabus & Deep-Dive Directory

### ☕ Module 1: Java Foundations
1. [OOP, SOLID, and Clean Code](01-java-foundations/01_OOP_SOLID_AND_CLEAN_CODE.md)
   - Encapsulation, Abstraction, Inheritance, Polymorphism in backend services.
   - SOLID principles applied to microservice architectures.
2. [Open Module Roadmap & Syllabus](01-java-foundations/README.md)

---

### 🌱 Module 2: Spring Boot Foundations — Planned
[Open Module Roadmap](02-spring-boot-foundations/README.md)  
Read in order: IoC/DI → Auto-configuration → MVC → Filters/AOP → Validation → Transactions.

---

### 🗄️ Module 3: Database & Persistence — Planned
[Open Module Roadmap](03-database-and-persistence/README.md)  
Read in order: SQL/indexes → PostgreSQL transactions → JPA lifecycle → Hibernate fetching → locking → Flyway → pagination/auditing.

---

### 🏛️ Module 4: Architecture Foundations
1. [Monolith vs. Microservices vs. Distributed Monolith](04-architecture-foundations/01_MONOLITH_VS_MICROSERVICES_VS_DISTRIBUTED_MONOLITH.md)
   - Why microservices fail without clear bounded contexts.
   - The "Distributed Monolith" anti-pattern and how to identify it.
2. [Database-per-Service & Polyglot Persistence](04-architecture-foundations/02_DATABASE_PER_SERVICE_AND_POLYGLOT_PERSISTENCE.md)
   - Why microservices must never share a database.
   - Relational (PostgreSQL) vs. In-Memory Key-Value (Redis).

---

### 🛡️ Module 5: Security & Identity Masterclass
1. [Stateful Sessions vs. Stateless JWTs](05-security-and-identity/01_STATEFUL_SESSIONS_VS_STATELESS_JWTS.md)
   - `JSESSIONID` memory bottlenecks vs. self-contained cryptographic claims.
   - Horizontal scaling without sticky sessions or session clustering.
2. [JWT Anatomy & Signature Mathematics](05-security-and-identity/02_JWT_ANATOMY_AND_SIGNATURE_MATHEMATICS.md)
   - Base64Url encoding vs. encryption.
   - Header, Payload, and Signature mechanics.
3. [Symmetric HMAC-SHA256 vs. Asymmetric RS256](05-security-and-identity/03_SYMMETRIC_HMAC_VS_ASYMMETRIC_RS256.md)
   - Shared secret vs. Private/Public Keypair.
   - Zero-Trust security: why the verifier must never hold the signing key.
4. [Why Do We Need JTI (JWT ID) & Revocation Strategies?](05-security-and-identity/04_WHY_DO_WE_NEED_JTI_AND_TOKEN_REVOCATION.md)
   - Single-device logout without revoking all customer sessions.
   - Defense against replay attacks and security auditing.
5. [Refresh Token Rotation & BCrypt Password Hashing](05-security-and-identity/05_REFRESH_TOKEN_ROTATION_AND_BCRYPT_MASTERCLASS.md)
   - Automatic token theft detection via database rotation.
   - Why BCrypt is intentionally slow with salt and adaptive work factors.

---

### 🧯 Module 6: API Design & Resilience — Planned
[Open Module Roadmap](06-api-design-and-resilience/README.md)  
REST design → Pagination → Idempotency → Timeouts/retries → Circuit breakers → Rate limiting/backpressure.

---

### ⚡ Module 7: Edge Routing & Reactive Gateway
1. [Netty Reactive Event Loops vs. Tomcat Thread Pools](07-edge-gateway-and-reactive/01_NETTY_REACTIVE_EVENT_LOOPS_VS_TOMCAT_THREAD_POOLS.md)
   - Non-blocking I/O multiplexing (`epoll`) vs. thread-per-request blocking.
   - How 16 Netty worker threads handle 50,000 concurrent connections with < 50MB RAM.
2. [Why the Gateway Must NOT Call User Service on Every Request](07-edge-gateway-and-reactive/02_WHY_GATEWAY_SHOULD_NOT_CALL_USER_SERVICE_ON_EVERY_REQUEST.md)
   - The double latency penalty, 10x load amplification, and single point of failure (SPOF).
   - In-memory cryptographic edge validation + downstream header mutation (`X-User-Id`).
3. [Distributed Rate Limiting: Token Bucket & Redis Lua Scripts](07-edge-gateway-and-reactive/03_DISTRIBUTED_RATE_LIMITING_TOKEN_BUCKET_AND_REDIS_LUA.md)
   - Token Bucket vs. Leaky Bucket vs. Fixed Window.
   - Atomic Lua execution preventing race conditions across multi-instance gateways.
4. [Centralized Edge CORS vs. The CORS Duplication Bug](07-edge-gateway-and-reactive/04_EDGE_CORS_VS_THE_CORS_DUPLICATION_BUG.md)
   - Browser preflight `OPTIONS` requests.
   - Why duplicate `Access-Control-Allow-Origin` headers break browsers.
5. [Distributed Tracing & The Correlation ID Pattern](07-edge-gateway-and-reactive/05_DISTRIBUTED_TRACING_AND_CORRELATION_ID_PATTERN.md)
   - TraceId vs. SpanId vs. CorrelationId.
   - End-to-end request tracking across 5+ services in production logs.

---

### 📡 Module 8: Service Discovery & Registry
1. [Client-Side vs. Server-Side Discovery](08-service-discovery/01_CLIENT_SIDE_VS_SERVER_SIDE_DISCOVERY.md)
   - Netflix Eureka (`lb://`) vs. AWS ALB / Kubernetes ClusterIP DNS.
   - Eliminating the middle proxy hop.
2. [Netflix Eureka & The CAP Theorem (AP vs. CP)](08-service-discovery/02_EUREKA_AND_THE_CAP_THEOREM_AP_VS_CP.md)
   - Why Eureka favors Availability over strict Consistency during network partitions.
   - Why ZooKeeper/Consul CP registries cause cascading microservice outages.
3. [Eureka Self-Preservation Mode & Zombie Instances](08-service-discovery/03_EUREKA_SELF_PRESERVATION_AND_ZOMBIE_INSTANCES.md)
   - Heartbeat lease renewals (30s) and eviction thresholds (90s).
   - How Self-Preservation protects against transient network partitions.

---

### 📨 Module 9: Kafka & Event-Driven Systems — Planned
[Open Module Roadmap](09-kafka-and-event-driven-systems/README.md)

---

### ✅ Module 10: Testing & Quality — Planned
[Open Module Roadmap](10-testing-and-quality/README.md)

---

### 📈 Module 11: Observability & Production — Planned
[Open module roadmap](11-observability-and-production/README.md)

### ☁️ Module 12: Deployment & Cloud — Planned
[Open module roadmap](12-deployment-and-cloud/README.md)

---

## 🎯 Pedagogical Standard of Every Document
Every single deep-dive in this knowledge base follows a strict 4-tier learning structure:
1. 🟢 **Tier 1: Intuitive Mental Model** — Analogies, visual diagrams, and plain English for beginners and interns.
2. 🟡 **Tier 2: Clearing Doubts & Anti-Patterns** — Answers the *"Why not the naive way?"* questions that puzzle junior developers.
3. 🔴 **Tier 3: Low-Level Internal Mechanics** — Byte-level formats, OS kernel thread models, and memory allocations for senior engineers.
4. 🏆 **Tier 4: Top 5 Tricky Tier-1 Interview Q&A** — Exact questions asked by Amazon, Netflix, Uber, and Microsoft with high-scoring answers.
