# E-Commerce Microservices Mastery (Java 21 & Spring Boot 3)

An end-to-end, interview-ready E-Commerce Microservices ecosystem built from scratch using **Java 21 LTS**, **Spring Boot 3.3+**, **Spring Cloud**, **Apache Kafka**, **PostgreSQL**, and **Redis**.

This project is built as an interactive, highly documented code reference designed to master core backend engineering, distributed systems, and senior interview concepts.

---

## 📖 Master Architecture & Learning Roadmap
- 🗺️ **[IMPLEMENTATION_PLAN.md](file:///c:/Users/Animesh/Desktop/Ecommercce-Microservices/IMPLEMENTATION_PLAN.md)**: Master technical blueprint, phased roadmap, and architecture guide.
- 📚 **[docs/ Knowledge Base](file:///c:/Users/Animesh/Desktop/Ecommercce-Microservices/docs/README.md)**: 12-Module Deep-Dive Masterclass & Tier-1 Interview Compendium.
  - [Module 01: Java Foundations](file:///c:/Users/Animesh/Desktop/Ecommercce-Microservices/docs/01-java-foundations/01_OOP_SOLID_AND_CLEAN_CODE.md)
  - [Module 04: Architecture Foundations](file:///c:/Users/Animesh/Desktop/Ecommercce-Microservices/docs/04-architecture-foundations/01_MONOLITH_VS_MICROSERVICES_VS_DISTRIBUTED_MONOLITH.md)
  - [Module 05: Security & Identity Masterclass](file:///c:/Users/Animesh/Desktop/Ecommercce-Microservices/docs/05-security-and-identity/01_STATEFUL_SESSIONS_VS_STATELESS_JWTS.md)
  - [Module 07: Edge Routing & Reactive Gateway](file:///c:/Users/Animesh/Desktop/Ecommercce-Microservices/docs/07-edge-gateway-and-reactive/01_NETTY_REACTIVE_EVENT_LOOPS_VS_TOMCAT_THREAD_POOLS.md)
  - [Module 08: Service Discovery & Netflix Eureka](file:///c:/Users/Animesh/Desktop/Ecommercce-Microservices/docs/08-service-discovery/01_CLIENT_SIDE_VS_SERVER_SIDE_DISCOVERY.md)
- 📘 **Service Master Guides:**
  - [User & Identity Service Guide](file:///c:/Users/Animesh/Desktop/Ecommercce-Microservices/user-service/USER_SERVICE_MASTER_GUIDE.md)
  - [Discovery Server (Eureka) Guide](file:///c:/Users/Animesh/Desktop/Ecommercce-Microservices/discovery-server/DISCOVERY_SERVER_MASTER_GUIDE.md)
  - [API Gateway (Spring Cloud Gateway) Guide](file:///c:/Users/Animesh/Desktop/Ecommercce-Microservices/api-gateway/API_GATEWAY_MASTER_GUIDE.md)

---

## 🏛️ Microservices Architecture

```
                                  [ Client (Web/Mobile) ]
                                             |
                                    HTTP/REST (Port 8080)
                                             v
                           +-----------------------------------+
                           |     Spring Cloud API Gateway      |
                           |  - Cryptographic JWT Verification |
                           |  - Redis Token-Bucket Rate Limit  |
                           |  - Header Propagation (X-User-Id) |
                           +-----------------+-----------------+
                                             |
            +--------------------+-----------+-----------+--------------------+
            |                    |                       |                    |
            v                    v                       v                    v
    +---------------+    +---------------+       +---------------+    +---------------+
    | Identity Svc  |    |  Product Svc  |       |   Cart Svc    |    |   Order Svc   |
    |  (Port 8081)  |    |  (Port 8082)  |       |  (Port 8084)  |    |  (Port 8085)  |
    | (identity_db) |    | (product_db)  |       |    (Redis)    |    |  (order_db)   |
    +---------------+    +-------+-------+       +---------------+    +-------+-------+
                                 ^                                            |
                                 +---- Sync OpenFeign (Price Snapshot) -------+
                                                                              |
==============================================================================| Events
|                                                                             v
|                         APACHE KAFKA DISTRIBUTED LOG                        |
|        Topics: order-events | inventory-events | payment-events             |
===============================================================================
               |                               |                      |
               v                               v                      v
       +---------------+               +---------------+      +---------------+
       | Inventory Svc |               |  Payment Svc  |      | Notification  |
       |  (Port 8083)  |               |  (Port 8086)  |      |  (Port 8087)  |
       |(inventory_db) |               | (payment_db)  |      |(notification) |
       +---------------+               +---------------+      +---------------+
```

---

## 🎓 Code Pedagogical Standards
The project aims to follow four learning rules; foundational classes currently contain the deepest explanations, while smaller support classes are being expanded incrementally:
1. **File Concept & Reading Order Header:** Explains what the file does, its design pattern, its exact position in the execution flow, and which file to read next.
2. **Deep In-Line Explanations:** Explaining **WHAT** each line/annotation does and **WHY** it was chosen over alternatives.
3. **Tricky Interview Q&A Callouts:** Real-world interview questions from top tech companies embedded directly with answers in the code.
4. **Zero Magic:** Internal proxy mechanics, reflection, and Spring lifecycle explained clearly.

---

## 🚀 Getting Started
Check [IMPLEMENTATION_PLAN.md](file:///c:/Users/Animesh/Desktop/Ecommercce-Microservices/IMPLEMENTATION_PLAN.md) for local setup, Docker Compose configuration, and the step-by-step development roadmap.
