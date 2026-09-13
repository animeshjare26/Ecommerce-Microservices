# Deep Dive 06: Database Migrations with Flyway and Zero-Downtime Schema Evolution

> **Module:** `03-database-and-persistence`  
> **Target Audience:** Beginner Interns to Principal DevOps & Backend Architects / Tier-1 Candidates  
> **Prerequisites:** SQL DDL (`CREATE`, `ALTER`, `DROP`), Docker, CI/CD  
> **Related Code in Project:** Flyway scripts in `user-service/src/main/resources/db/migration/`  
> **Last Verified Against:** Flyway 10 / Spring Boot 3.3.2 / PostgreSQL 16  

---

## 🗺️ Visual Reading Order & Navigation
```text
[05_CONCURRENCY_CONTROL_OPTIMISTIC_VS_PESSIMISTIC_LOCKING.md]
                         │
                         ▼
[06_DATABASE_MIGRATIONS_WITH_FLYWAY_AND_ZERO_DOWNTIME_SCHEMA_EVOLUTION.md]  ◄── YOU ARE HERE
                         │
                         ▼
[04-architecture-foundations/README.md]
```

---

## 🟢 Tier 1: The Intuitive Mental Model

### The Airport Runway Resurfacing Analogy
Imagine an international airport with 1,000 flights landing daily:
- **The Naive Downtime Way (`ddl-auto=update` or locking `ALTER TABLE`):**
  You shut down the entire airport for 8 hours on Saturday night to repave the runway. All incoming planes are turned away or crash. This is unacceptable for modern 24/7 global businesses.
- **The Zero-Downtime Expand-Contract Way:**
  1. **Expand:** You construct a brand new parallel runway next to the old one while flights continue using the old runway uninterrupted.
  2. **Transition:** You gradually redirect new arriving flights to the new runway.
  3. **Contract:** Once all flights are safely landing on the new runway, you quietly dismantle the old runway with zero disruption to air traffic!

---

## 🟡 Tier 2: Common Doubts, Gotchas & Anti-Patterns

### 1. Why `spring.jpa.hibernate.ddl-auto = update` is Forbidden in Production
In university projects and beginner tutorials, you often see:
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update # DANGER: STRICTLY BANNED IN PRODUCTION!
```
**Why this will destroy your company's database:**
1. **Never Drops Columns:** If you rename a Java field from `phone` to `phoneNumber`, Hibernate does not rename the column. It creates a brand-new column `phone_number` and leaves `phone` orphaned, silently corrupting data!
2. **Table-Locking Disasters:** On a table with 50,000,000 rows, Hibernate generating an `ALTER TABLE ADD COLUMN ... NOT NULL` without a default value will acquire an **`AccessExclusiveLock`**, freezing all reads and writes across the entire platform for hours!
3. **Zero Versioning / Auditability:** Nobody knows who changed the schema, when it changed, or how to reproduce it across Staging, QA, and Production.

> [!CAUTION]
> In production, always set:
> ```yaml
> spring:
>   jpa:
>     hibernate:
>       ddl-auto: validate # Checks schema matches entities; DOES NOT ALTER SCHEMA!
> ```

---

### 2. The Flyway Checksum Mismatch Error
You deploy a migration script `V2__add_discount_to_products.sql`.  
The next day, another developer edits that exact file to fix a typo and pushes to Git.

When the application boots in production, Flyway fails with:
`FlywayException: Validate failed: Migration checksum mismatch for migration version 2`

**Why Flyway halts the application:**
- When Flyway executes a script, it calculates a **CRC32 / SHA-256 Checksum** of the file contents and stores it in the `flyway_schema_history` table.
- Modifying an already applied script means the code in Git no longer matches the database in production.
- **Rule:** **Never modify an already committed migration script!** Always create a new sequential migration script: `V3__fix_discount_column_type.sql`!

---

## 🔴 Tier 3: The Expand-Contract (Parallel Run) Pattern

How do you perform a non-backward-compatible database change (e.g. **renaming a column from `phone` to `mobile_number`**) while running rolling updates with zero downtime?

```text
Phase 1 (Expand):       DB has BOTH [phone] and [mobile_number]
                        App v1 reads/writes [phone]

Phase 2 (Dual-Write):   App v2 writes to BOTH [phone] & [mobile_number]
                        App v2 reads from [mobile_number] (fallback to [phone])

Phase 3 (Backfill):     Background migration copies historical data:
                        UPDATE users SET mobile_number = phone WHERE mobile_number IS NULL;

Phase 4 (Cutover):      App v3 reads/writes ONLY [mobile_number]

Phase 5 (Contract):     Flyway script drops column [phone] safely!
```

By following these 5 phases:
- Old instances of the app (v1) and new instances of the app (v2) can run concurrently during rolling deployments without crashing!
- No database table locks are held.
- Rollback is always safely possible at any phase!

---

## 🏆 Tier 4: Top 5 Tricky Tier-1 Interview Questions & Answers

### Q1: How does Flyway ensure that two microservice instances starting simultaneously don't execute migrations concurrently?
**High-Scoring Answer:**
> "Flyway uses **Database-Level Advisory Locks**.
> 
> When Flyway begins migration on PostgreSQL, it executes:
> `SELECT pg_advisory_lock(hash('flyway_lock'))`
> 
> 1. The first microservice instance acquires the lock.
> 2. The second microservice instance blocks and waits.
> 3. The first instance inspects `flyway_schema_history`, executes any pending `V*.sql` scripts inside a transaction, updates the history table, and releases the lock.
> 4. When the second instance acquires the lock, it reads `flyway_schema_history`, sees that the database is already up to date, and proceeds directly to starting the application."

---

### Q2: How do you add a `NOT NULL` column to a table with 100,000,000 rows in PostgreSQL without downtime?
**The Dangerous Way:**
`ALTER TABLE orders ADD COLUMN loyalty_points INT NOT NULL DEFAULT 0;`  
*(In older PG versions, this rewrites the entire 100M row table, locking it for 20 minutes).*

**The Zero-Downtime Staff-Level Answer (PostgreSQL):**
> "In PostgreSQL 11+, adding a column with a constant `DEFAULT` is metadata-only (zero lock).
> 
> However, to guarantee zero locking across all databases:
> 1. **Add the column as NULLABLE (instant metadata update):**
>    `ALTER TABLE orders ADD COLUMN loyalty_points INT;`
> 2. **Backfill in small batches (avoiding lock contention):**
>    Run a background script: `UPDATE orders SET loyalty_points = 0 WHERE id BETWEEN ? AND ?;`
> 3. **Add a NOT VALID check constraint (instant metadata update):**
>    `ALTER TABLE orders ADD CONSTRAINT check_loyalty_not_null CHECK (loyalty_points IS NOT NULL) NOT VALID;`
> 4. **Validate constraint without locking:**
>    `ALTER TABLE orders VALIDATE CONSTRAINT check_loyalty_not_null;` (Checks rows without holding exclusive write locks!)."

---

### Q3: What is the difference between Versioned Migrations, Repeatable Migrations, and Undo Migrations in Flyway?
**High-Scoring Answer:**
> - **Versioned (`V<version>__<description>.sql`):** Unique sequential version number (e.g. `V1__init.sql`). Applied once and only once. Checksum is strictly verified.
> - **Repeatable (`R__<description>.sql`):** Has no version number. Re-executed whenever its checksum changes. Ideal for reproducible database objects like **Stored Procedures, Functions, and Views**.
> - **Undo (`U<version>__<description>.sql`):** Rolls back the corresponding versioned migration. (Available in Flyway Enterprise; rarely used in CI/CD since forward-fixing via `V(N+1)` is safer)."

---

### Q4: What does Flyway Baseline (`flyway.baseline-on-migrate = true`) do?
**High-Scoring Answer:**
> "When introducing Flyway to an existing legacy database that already contains tables:
> Running Flyway would normally fail because it expects an empty database.
> 
> Enabling `baseline-on-migrate: true` tells Flyway:
> *'If the `flyway_schema_history` table does not exist, do not crash! Mark the existing state of this database as Baseline Version 1, and only apply scripts with versions greater than V1 (e.g. V2, V3)'*."

---

### Q5: How do you create an index concurrently in PostgreSQL through Flyway?
**High-Scoring Answer:**
> "Normal `CREATE INDEX` locks the table against writes. In production, we must use **`CREATE INDEX CONCURRENTLY`**, which builds the index without blocking writes.
> 
> However, PostgreSQL forbids `CREATE INDEX CONCURRENTLY` from running inside a transaction block!
> To execute it in Flyway, you must disable transactions for that specific script by adding a configuration header at the very top of the SQL file:
> ```sql
> -- flyway:non-transactional=true
> CREATE INDEX CONCURRENTLY idx_users_phone ON users (phone);
> ```"
