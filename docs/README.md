# E-Commerce Microservices Mastery: Architectural Knowledge Base & Interview Compendium

> **An interactive, pedagogical masterclass in cloud-native microservices engineering. Built to eliminate all "magic", bridge the gap between beginner intuition and staff-engineer depth, and prepare you for Tier-1 system design and backend coding interviews.**

---

## 🗺️ Master Curriculum & Reading Map

```text
                                  [ START HERE ]
                                         │
                                         ▼
                 ┌───────────────────────────────────────────────┐
                 │    01. ARCHITECTURE FOUNDATIONS               │
                 │  - Monolith vs. Microservices vs. Distributed │
                 │  - Database-per-Service & Polyglot Storage    │
                 └───────────────────────┬───────────────────────┘
                                         │
                                         ▼
                 ┌───────────────────────────────────────────────┐
                 │    02. SECURITY & IDENTITY MASTERCLASS        │
                 │  - Stateful Sessions vs. Stateless JWTs       │
                 │  - JWT Anatomy & Cryptographic Math           │
                 │  - Symmetric HMAC vs. Asymmetric RS256        │
                 │  - Why We Need JTI (Single-Session Logout)    │
                 │  - Refresh Token Rotation & BCrypt Hashing    │
                 └───────────────────────┬───────────────────────┘
                                         │
                                         ▼
                 ┌───────────────────────────────────────────────┐
                 │    03. EDGE ROUTING & REACTIVE GATEWAY        │
                 │  - Netty Event Loops vs. Tomcat Threads       │
                 │  - Why Gateway Doesn't Call User Service      │
                 │  - Distributed Rate Limiting (Token Bucket)   │
                 │  - Centralized Edge CORS vs. Duplication Bug  │
                 │  - Distributed Tracing & Correlation IDs      │
                 └───────────────────────┬───────────────────────┘
                                         │
                                         ▼
                 ┌───────────────────────────────────────────────┐
                 │    04. SERVICE DISCOVERY & REGISTRY           │
                 │  - Client-Side vs. Server-Side Discovery      │
                 │  - Netflix Eureka & CAP Theorem (AP vs. CP)   │
                 │  - Self-Preservation Mode & Zombie Instances  │
                 └───────────────────────────────────────────────┘
```

---

## 📑 Syllabus & Deep-Dive Directory

### 🏛️ Module 1: Architecture Foundations
1. [Monolith vs. Microservices vs. Distributed Monolith](01-architecture-foundations/01_MONOLITH_VS_MICROSERVICES_VS_DISTRIBUTED_MONOLITH.md)
   - Why microservices fail without clear bounded contexts.
   - The "Distributed Monolith" anti-pattern and how to identify it.
2. [Database-per-Service & Polyglot Persistence](01-architecture-foundations/02_DATABASE_PER_SERVICE_AND_POLYGLOT_PERSISTENCE.md)
   - Why microservices must never share a database.
   - Relational (PostgreSQL) vs. In-Memory Key-Value (Redis).

---

### 🛡️ Module 2: Security & Identity Masterclass
1. [Stateful Sessions vs. Stateless JWTs](02-security-and-identity/01_STATEFUL_SESSIONS_VS_STATELESS_JWTS.md)
   - `JSESSIONID` memory bottlenecks vs. self-contained cryptographic claims.
   - Horizontal scaling without sticky sessions or session clustering.
2. [JWT Anatomy & Signature Mathematics](02-security-and-identity/02_JWT_ANATOMY_AND_SIGNATURE_MATHEMATICS.md)
   - Base64Url encoding vs. encryption.
   - Header, Payload, and Signature mechanics.
3. [Symmetric HMAC-SHA256 vs. Asymmetric RS256](02-security-and-identity/03_SYMMETRIC_HMAC_VS_ASYMMETRIC_RS256.md)
   - Shared secret vs. Private/Public Keypair.
   - Zero-Trust security: why the verifier must never hold the signing key.
4. [Why Do We Need JTI (JWT ID) & Revocation Strategies?](02-security-and-identity/04_WHY_DO_WE_NEED_JTI_AND_TOKEN_REVOCATION.md)
   - Single-device logout without revoking all customer sessions.
   - Defense against replay attacks and security auditing.
5. [Refresh Token Rotation & BCrypt Password Hashing](02-security-and-identity/05_REFRESH_TOKEN_ROTATION_AND_BCRYPT_MASTERCLASS.md)
   - Automatic token theft detection via database rotation.
   - Why BCrypt is intentionally slow with salt and adaptive work factors.

---

### ⚡ Module 3: Edge Routing & Reactive Gateway
1. [Netty Reactive Event Loops vs. Tomcat Thread Pools](03-edge-gateway-and-reactive/01_NETTY_REACTIVE_EVENT_LOOPS_VS_TOMCAT_THREAD_POOLS.md)
   - Non-blocking I/O multiplexing (`epoll`) vs. thread-per-request blocking.
   - How 16 Netty worker threads handle 50,000 concurrent connections with < 50MB RAM.
2. [Why the Gateway Must NOT Call User Service on Every Request](03-edge-gateway-and-reactive/02_WHY_GATEWAY_SHOULD_NOT_CALL_USER_SERVICE_ON_EVERY_REQUEST.md)
   - The double latency penalty, 10x load amplification, and single point of failure (SPOF).
   - In-memory cryptographic edge validation + downstream header mutation (`X-User-Id`).
3. [Distributed Rate Limiting: Token Bucket & Redis Lua Scripts](03-edge-gateway-and-reactive/03_DISTRIBUTED_RATE_LIMITING_TOKEN_BUCKET_AND_REDIS_LUA.md)
   - Token Bucket vs. Leaky Bucket vs. Fixed Window.
   - Atomic Lua execution preventing race conditions across multi-instance gateways.
4. [Centralized Edge CORS vs. The CORS Duplication Bug](03-edge-gateway-and-reactive/04_EDGE_CORS_VS_THE_CORS_DUPLICATION_BUG.md)
   - Browser preflight `OPTIONS` requests.
   - Why duplicate `Access-Control-Allow-Origin` headers break browsers.
5. [Distributed Tracing & The Correlation ID Pattern](03-edge-gateway-and-reactive/05_DISTRIBUTED_TRACING_AND_CORRELATION_ID_PATTERN.md)
   - TraceId vs. SpanId vs. CorrelationId.
   - End-to-end request tracking across 5+ services in production logs.

---

### 📡 Module 4: Service Discovery & Registry
1. [Client-Side vs. Server-Side Discovery](04-service-discovery/01_CLIENT_SIDE_VS_SERVER_SIDE_DISCOVERY.md)
   - Netflix Eureka (`lb://`) vs. AWS ALB / Kubernetes ClusterIP DNS.
   - Eliminating the middle proxy hop.
2. [Netflix Eureka & The CAP Theorem (AP vs. CP)](04-service-discovery/02_EUREKA_AND_THE_CAP_THEOREM_AP_VS_CP.md)
   - Why Eureka favors Availability over strict Consistency during network partitions.
   - Why ZooKeeper/Consul CP registries cause cascading microservice outages.
3. [Eureka Self-Preservation Mode & Zombie Instances](04-service-discovery/03_EUREKA_SELF_PRESERVATION_AND_ZOMBIE_INSTANCES.md)
   - Heartbeat lease renewals (30s) and eviction thresholds (90s).
   - How Self-Preservation protects against transient network partitions.

---

## 🎯 Pedagogical Standard of Every Document
Every single deep-dive in this knowledge base follows a strict 4-tier learning structure:
1. 🟢 **Tier 1: Intuitive Mental Model** — Analogies, visual diagrams, and plain English for beginners and interns.
2. 🟡 **Tier 2: Clearing Doubts & Anti-Patterns** — Answers the *"Why not the naive way?"* questions that puzzle junior developers.
3. 🔴 **Tier 3: Low-Level Internal Mechanics** — Byte-level formats, OS kernel thread models, and memory allocations for senior engineers.
4. 🏆 **Tier 4: Top 5 Tricky Tier-1 Interview Q&A** — Exact questions asked by Amazon, Netflix, Uber, and Microsoft with high-scoring answers.
