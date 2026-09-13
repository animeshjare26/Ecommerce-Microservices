# Deep Dive 02: Database-per-Service & Polyglot Persistence

> **Module:** `01-architecture-foundations`  
> **Target Audience:** From Beginner Intern to Principal Architect  
> **Core Concept:** Database-per-Service Pattern, Polyglot Persistence, Relational (PostgreSQL) vs. In-Memory (Redis), Schema Independence.

> **Status:** Conceptual
>
> **Related code:** `docker-compose.yml`, `user-service/src/main/resources/db/migration/V1__init_user_schema.sql`
>
> **Last verified against:** Spring Boot 3.3.2

---

## 🟢 Tier 1: The Intuitive Mental Model (For Beginners & Interns)

### The Kitchen Pantry vs. Personal Toolboxes

Imagine a shared kitchen in a college apartment:

#### 1. The Shared Database Anti-Pattern (One Shared Refrigerator):
- 5 roommates share a single refrigerator.
- Roommate A buys groceries for a dinner party.
- Roommate B drinks the milk without asking; Roommate C rearranges the shelves; Roommate D leaves the door open and spoils everything!
- **In Software:** If `user-service`, `order-service`, and `cart-service` all connect to the same PostgreSQL database, an unindexed slow query in `order-service` will lock tables, exhaust connection pools, and **crash `user-service` and `cart-service` too!**

#### 2. The Database-per-Service Model (Private Mini-Fridges):
- Every roommate has their own private mini-fridge inside their locked bedroom.
- If Roommate A leaves their fridge door open, **nobody else's food is affected!**
- If you want milk from Roommate A, you knock on their door and ask (**API Request**).

```
┌─────────────────────────────────────────────────────────────────────────┐
│ SHARED DATABASE ANTI-PATTERN:                                           │
│ [ user-service ] ───┐                                                   │
│ [ cart-service ] ───┼──► [ SINGLE MONOLITHIC DATABASE ]                 │
│ [ order-service] ───┘                                                   │
│ ❌ Schema changes in one service break others. Lock contention & crashes│
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│ DATABASE-PER-SERVICE & POLYGLOT PERSISTENCE (Our Architecture):         │
│ [ user-service ] ─────► PostgreSQL (user_db)       - ACID Transactions  │
│ [ cart-service ] ─────► Redis (cart:{userId})       - Sub-millisecond TTL│
│ [ api-gateway  ] ─────► Redis (blocklist:jti:*)     - In-Memory Lookup   │
│ ✅ 100% schema isolation. Optimized storage engine for every workload!   │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 🟡 Tier 2: Clearing Common Doubts & Anti-Patterns

### Doubt 1: "Does Database-per-Service mean I need 10 separate physical database servers?"
**NO! This is a common misconception:**
- In production, you can deploy a managed cloud cluster (e.g. AWS RDS PostgreSQL) that hosts **multiple isolated logical databases** (`user_db`, `product_db`, `order_db`), each with its own separate database user credentials, schemas, and connection pools!
- In our project's [docker-compose.yml](file:///c:/Users/Animesh/Desktop/Ecommercce-Microservices/docker-compose.yml), a single PostgreSQL 16 container automatically boots up and initializes isolated logical databases via `init-databases.sql`.
- **The Rule:** What matters is **Strict Schema Ownership**—`cart-service` has zero credentials or SQL permissions to query `user_db` directly!

### Doubt 2: "What is Polyglot Persistence?"
Using the **right database engine for the right job**, rather than forcing everything into a relational table:
- **Shopping Cart (`cart-service`):** Ephemeral data, frequently updated, needs to expire after 7 days if abandoned.
  $\rightarrow$ **Redis** (In-Memory Key-Value with native TTL) is 100x faster than writing temporary rows to PostgreSQL.
- **Financial Orders & User Passwords:** Strict ACID guarantees, foreign keys, unique email constraints.
  $\rightarrow$ **PostgreSQL** (Relational ACID database) is ideal.

---

## 🔴 Tier 3: Low-Level Internal Mechanics (For Senior Engineers)

### Why Relational Foreign Keys Are Forbidden Across Microservices

In a monolith:
```sql
ALTER TABLE orders ADD CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id);
```
- In microservices, `orders` is in `order_db` and `users` is in `user_db`.
- **Database engines cannot enforce foreign keys across network boundaries!**
- **How referential integrity is preserved in microservices:**
  1. **Eventual Consistency:** When a user is registered, a domain event is published.
  2. **Compensating Transactions (Saga Pattern):** If an order is created for an invalid user, the order verification fails and executes a compensation (cancelling the order).
  3. **Application-Level Validation:** The service validates the presence of foreign IDs via lightweight cached lookups.

---

## 🏆 Tier 4: Top 5 Tricky Tier-1 Interview Questions & Winning Answers

### Q1: Why is Two-Phase Commit (2PC / XA Distributed Transactions) considered an anti-pattern in modern cloud microservices?
**Answer:**
2PC requires a central transaction coordinator to lock database rows across all microservices simultaneously until all nodes confirm "PREPARED".
- **Why it fails at scale:** If Node 3 suffers a 2-second network timeout, **locks on Node 1 and Node 2 remain held**, blocking all incoming customer traffic and creating system-wide deadlock!
- **Modern Solution:** The **Saga Pattern** (Choreographed or Orchestrated) using local database transactions followed by compensating actions if a downstream step fails.

### Q2: What is "Schema Migration Drift", and how does Flyway prevent it?
**Answer:**
When multiple developers or environments run database updates manually, schemas become inconsistent across dev, staging, and production.
- **Flyway Solution:** SQL migration scripts are version-controlled in Git (`V1__init_user_schema.sql`, `V2__add_phone_number.sql`).
- At startup, Flyway acquires a database lock, inspects the `flyway_schema_history` table, and executes pending migrations in strict sequential order. If an entity doesn't match the migration, Hibernate validation fails fast on startup!

### Q3: How do you handle database backups with the Database-per-Service pattern?
**Answer:**
Each service's database can have an **independent backup schedule tailored to its criticality and churn**:
- `order_db`: Continuous point-in-time recovery (WAL archiving) with 5-minute RPO (Recovery Point Objective).
- `cart_service` (Redis): Ephemeral RDB snapshots every 6 hours (losing carts is inconvenient, but losing financial orders is catastrophic).

### Q4: What is the "Outbox Pattern" and why is it required when modifying a database and publishing events?
**Answer:**
Writing to a database and publishing to Apache Kafka are two separate network operations that cannot share a transaction.
- **The Outbox Pattern:** The service writes the business entity (e.g. `Order`) AND the event payload (into an `outbox_events` table) within the **SAME local ACID transaction**.
- A separate background CDC worker (like Debezium) or scheduled polling relay reads the outbox table and streams events to Kafka reliably with **At-Least-Once delivery guarantees**!

### Q5: How do you prevent connection pool exhaustion when multiple microservices share an RDS instance?
**Answer:**
PostgreSQL processes have a global maximum connection limit (`max_connections`, typically 100–500). If 10 microservices each configure a HikariCP pool of 50 connections ($10 \times 50 = 500$), a traffic spike will exhaust PostgreSQL connections!
- **Mitigation:**
  1. Size HikariCP pools conservatively based on the formula: $\text{Connections} = (\text{CPU Cores} \times 2) + \text{Effective Spindle Count}$ (typically 10 connections per service).
  2. Deploy **AWS RDS Proxy** or **PgBouncer** between microservices and PostgreSQL for connection multiplexing and transaction-level pooling.
