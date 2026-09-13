# Deep Dive 02: Transactions, ACID Properties, Isolation Levels, and PostgreSQL MVCC Internals

> **Module:** `03-database-and-persistence`  
> **Target Audience:** Beginner Interns to Staff Database Architects / Tier-1 Interview Candidates  
> **Prerequisites:** SQL Basics, Spring `@Transactional` (Module 02)  
> **Related Code in Project:** PostgreSQL database transactions in `user-service`, `product-service` inventory  
> **Last Verified Against:** PostgreSQL 16 / Spring Boot 3.3.2  

---

## 🗺️ Visual Reading Order & Navigation
```text
[01_INDEXING_INTERNALS_B_TREES_AND_EXPLAIN_ANALYZE.md]
                         │
                         ▼
[02_TRANSACTIONS_ACID_AND_ISOLATION_LEVELS.md]  ◄── YOU ARE HERE
                         │
                         ▼
[03_JPA_ENTITY_LIFECYCLE_FIRST_LEVEL_CACHE_DIRTY_CHECKING.md]
```

---

## 🟢 Tier 1: The Intuitive Mental Model

### The Hotel Reservation Ledger
Imagine a popular boutique hotel with 1 remaining penthouse suite:
1. **Dirty Read (Reading uncommitted draft notes):**
   - Front Desk Clerk A writes a pencil note on the ledger: *"Reserved for Mr. Smith"*.
   - Clerk B glances over, sees the pencil note, and tells a walking customer: *"Sorry, suite is taken."*
   - Meanwhile, Mr. Smith's credit card is declined, so Clerk A erases the pencil note! Clerk B turned away a paying customer based on a ghost reservation that was never committed!
2. **Non-Repeatable Read (Values mutating under your feet):**
   - Clerk A opens the guest profile and reads: `Loyalty Tier: Silver`.
   - Before Clerk A finishes issuing the keycard, Clerk B updates the guest to `Loyalty Tier: Platinum` and commits.
   - Clerk A looks back at the screen: it now says `Platinum`! In the exact same conversation, reading the same row twice returned two different values!
3. **Phantom Read (Rows appearing out of nowhere):**
   - Clerk A queries: *"How many rooms are booked on Floor 4?"* ──► Answer: 5 rooms.
   - Clerk B books Room 408 on Floor 4 and commits.
   - Clerk A runs the exact same query again: ──► Answer: 6 rooms! A "phantom" room appeared out of thin air!

---

## 🟡 Tier 2: The 4 SQL Isolation Levels vs. Anomalies

| Isolation Level | Dirty Read | Non-Repeatable Read | Phantom Read | Serialization Anomaly |
| :--- | :---: | :---: | :---: | :---: |
| **Read Uncommitted** | ❌ Allowed | ❌ Allowed | ❌ Allowed | ❌ Allowed |
| **Read Committed** *(PG Default)* | 🟢 **Prevented** | ❌ Allowed | ❌ Allowed | ❌ Allowed |
| **Repeatable Read** | 🟢 **Prevented** | 🟢 **Prevented** | 🟢 **Prevented** *(in PG)* | ❌ Allowed (Write Skew) |
| **Serializable** | 🟢 **Prevented** | 🟢 **Prevented** | 🟢 **Prevented** | 🟢 **Prevented** |

> [!NOTE]
> In PostgreSQL, **Read Uncommitted is treated as Read Committed** because PostgreSQL's MVCC architecture physically cannot perform dirty reads!

---

## 🔴 Tier 3: Low-Level Internal Mechanics: PostgreSQL MVCC & Tuple Headers

Why is PostgreSQL celebrated for high concurrency?  
**Because READERS NEVER BLOCK WRITERS, and WRITERS NEVER BLOCK READERS!**

How does PostgreSQL achieve this without locking every row during a `SELECT`?  
Through **Multi-Version Concurrency Control (MVCC)**!

### 1. The Hidden Tuple Headers: `xmin` and `xmax`
Every physical row (tuple) in a PostgreSQL table has hidden system attributes:
- **`xmin`:** The Transaction ID (XID) of the transaction that **created/inserted** this row version.
- **`xmax`:** The Transaction ID of the transaction that **updated/deleted** this row version (defaults to 0 for live rows).

```text
Table: users (Physical Disk Representation)
┌────┬─────────┬──────────────┬────────┬────────┬─────────────────────────┐
│ id │ email   │ status       │ xmin   │ xmax   │ State                   │
├────┼─────────┼──────────────┼────────┼────────┼─────────────────────────┤
│ 1  │ a@b.com │ PENDING      │ 1001   │ 1005   │ DEAD (Replaced by Tx 1005)│
│ 1  │ a@b.com │ ACTIVE       │ 1005   │ 0      │ LIVE (Created by Tx 1005) │
└────┴─────────┴──────────────┴────────┴────────┴─────────────────────────┘
```

### 2. What happens during an `UPDATE` in PostgreSQL?
PostgreSQL **never overwrites data in place** on disk!
1. When Tx 1005 runs `UPDATE users SET status = 'ACTIVE' WHERE id = 1;`:
2. PostgreSQL does NOT modify the original tuple. Instead, it marks the original tuple's `xmax = 1005`.
3. It appends a **brand new physical tuple** at the end of the disk page with `xmin = 1005, xmax = 0`.
4. Any active transaction that started before Tx 1005 committed will continue reading the old tuple (`status = 'PENDING'`), while new transactions see the new tuple (`status = 'ACTIVE'`).

### 3. Table Bloat & The Vital Role of `VACUUM`
Because old tuples remain on disk, tables will continuously grow in size (**Table Bloat**) if left unchecked.
PostgreSQL runs a background daemon called **`autovacuum`**:
- Scans tables for tuples where `xmax` is older than the oldest running transaction.
- Marks that disk space as reusable for future `INSERT`s without shrinking the file on the OS.

---

## 🏆 Tier 4: Top 5 Tricky Tier-1 Interview Questions & Answers

### Q1: What is the Write Skew anomaly, and why doesn't Repeatable Read prevent it?
**High-Scoring Answer:**
> "Write Skew occurs when two concurrent transactions read overlapping data, satisfy a business invariant independently, and make disjoint updates that collectively violate the constraint.
> 
> **The On-Call Doctor Classic Example:**
> - Invariant: At least one doctor must be on call at all times. Currently, Dr. Alice and Dr. Bob are on call.
> - Tx 1 (Alice) checks: *'How many doctors on call?'* (Result: 2). Alice takes sick leave: `UPDATE doctors SET on_call = false WHERE name = 'Alice'`.
> - Tx 2 (Bob) checks concurrently: *'How many doctors on call?'* (Result: 2). Bob takes sick leave: `UPDATE doctors SET on_call = false WHERE name = 'Bob'`.
> - Both transactions commit under `Repeatable Read` because neither transaction updated the exact same row!
> - **Result:** Zero doctors are on call—violating the business rule!
> - **Solution:** Use **Serializable Isolation** (which uses Serializable Snapshot Isolation - SSI locks) or explicit row locks (`SELECT ... FOR UPDATE`)."

---

### Q2: How does the Write-Ahead Log (WAL) guarantee the "Durability" (D in ACID) without writing directly to table files on every commit?
**High-Scoring Answer:**
> "Writing updated table pages directly to random disk sectors on every transaction commit would cause devastating disk head latency.
> 
> Instead, relational databases use a **Write-Ahead Log (WAL)**:
> 1. When a transaction modifies a row, the change is applied to data pages in memory (**Buffer Pool**), marking them as 'dirty pages'.
> 2. Simultaneously, an append-only sequential log entry describing the byte-level difference is flushed to the WAL file on disk (`fsync`). Sequential disk writes are orders of magnitude faster than random table updates.
> 3. Once the WAL write is acknowledged on disk, the transaction responds `COMMIT SUCCESS` to the client.
> 4. If the power cuts 1 microsecond later, the database recovers on reboot by replaying the unapplied WAL records against the table data files during Crash Recovery (ARIES protocol)."

---

### Q3: What is the difference between Optimistic Concurrency Control and Pessimistic Concurrency Control?
| Dimension | Optimistic Concurrency Control | Pessimistic Concurrency Control |
| :--- | :--- | :--- |
| **Mechanism** | Application-level `@Version` integer/timestamp column. No DB locks held during user think time. | Database-level row locking (`SELECT FOR UPDATE`). |
| **SQL Executed** | `UPDATE item SET stock = stock - 1, version = version + 1 WHERE id = 1 AND version = 5` | `SELECT * FROM item WHERE id = 1 FOR UPDATE` |
| **Conflict Handling** | If rows updated == 0, throws `OptimisticLockException`. Client retries. | Concurrent transactions block and wait until the lock holder commits. |
| **Best Used When** | Low to moderate write contention (Product catalog, user profiles). | High write contention (Flash sales, hotel seat booking, bank account transfers). |

---

### Q4: What is a Transaction ID (XID) wraparound failure in PostgreSQL, and how does it bring down production?
**High-Scoring Answer:**
> "PostgreSQL uses 32-bit integers for Transaction IDs, which caps the total number of transactions at ~4.29 billion.
> 
> Because transactions are compared modulo $2^{31}$ to distinguish past vs. future transactions:
> If a database executes 2 billion transactions without vacuuming, older committed transaction IDs suddenly appear to be in the **future**, causing past committed data to become invisible!
> 
> To prevent total data corruption, PostgreSQL enforces a safety stop: when remaining transactions drop below 10,000,000, **PostgreSQL shuts down into read-only mode and refuses all writes** until an aggressive manual `VACUUM FREEZE` completes. Sizing and monitoring `autovacuum_freeze_max_age` is mandatory for production DBAs."

---

### Q5: How do you set isolation levels in Spring Boot for a specific `@Transactional` method?
**High-Scoring Answer:**
> "In Spring, you declare it directly on the annotation:
> ```java
> @Transactional(isolation = Isolation.REPEATABLE_READ)
> public OrderSummary calculateOrderAudit(UUID orderId) { ... }
> ```
> Under the hood, Spring's `DataSourceTransactionManager` executes a native connection statement: `SET TRANSACTION ISOLATION LEVEL REPEATABLE READ` before executing the user's queries, and restores the default isolation level when returning the connection to the HikariCP pool."
