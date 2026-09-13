# E-Commerce Microservices Mastery (Java 21 & Spring Boot 3)

An end-to-end, interview-ready E-Commerce Microservices ecosystem built from scratch using **Java 21 LTS**, **Spring Boot 3.3+**, **Spring Cloud**, **Apache Kafka**, **PostgreSQL**, and **Redis**.

This project is built as an interactive, highly documented code reference designed to master core backend engineering, distributed systems, and senior interview concepts.

---

## 📖 Master Architecture & Learning Roadmap
For the complete technical blueprint, phased roadmap, database breakdown, request flows, and interview guides, see:
👉 **[IMPLEMENTATION_PLAN.md](file:///c:/Users/Animesh/Desktop/Ecommercce-Microservices/IMPLEMENTATION_PLAN.md)**

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
Every file in this project strictly follows four learning rules:
1. **File Concept & Reading Order Header:** Explains what the file does, its design pattern, its exact position in the execution flow, and which file to read next.
2. **Deep In-Line Explanations:** Explaining **WHAT** each line/annotation does and **WHY** it was chosen over alternatives.
3. **Tricky Interview Q&A Callouts:** Real-world interview questions from top tech companies embedded directly with answers in the code.
4. **Zero Magic:** Internal proxy mechanics, reflection, and Spring lifecycle explained clearly.

---

## 🚀 Getting Started
Check [IMPLEMENTATION_PLAN.md](file:///c:/Users/Animesh/Desktop/Ecommercce-Microservices/IMPLEMENTATION_PLAN.md) for local setup, Docker Compose configuration, and the step-by-step development roadmap.
