# Module 3: Database and Persistence

> **Status:** 🟢 Complete (All 6 Deep-Dives Live)
>
> **Prerequisites:** Module 1 and Module 2.
>
> **Outcome:** Design correct SQL schemas and reason about JPA behavior, transactions, performance, and concurrency.

Read these lessons in order:

1. [Database Indexing Internals: B-Trees, Composite Indexes, and EXPLAIN ANALYZE](01_INDEXING_INTERNALS_B_TREES_AND_EXPLAIN_ANALYZE.md)
   - B-Tree high branching factors, leaf pointers, and composite index leftmost prefix rule.
   - Index Scan vs Index Only Scan vs Sequential Scan.
   - Why PostgreSQL deliberately skips indexes on low-cardinality data.
2. [Transactions, ACID Properties, Isolation Levels, and PostgreSQL MVCC Internals](02_TRANSACTIONS_ACID_AND_ISOLATION_LEVELS.md)
   - Dirty Read, Non-Repeatable Read, Phantom Read, and Write Skew anomalies.
   - Multi-Version Concurrency Control (MVCC) tuple headers (`xmin`, `xmax`).
   - Why readers never block writers, table bloat, and `autovacuum` tuning.
3. [JPA Entity Lifecycle, The First-Level Cache, and Automatic Dirty Checking](03_JPA_ENTITY_LIFECYCLE_FIRST_LEVEL_CACHE_DIRTY_CHECKING.md)
   - 4 Entity States: Transient, Managed, Detached, Removed.
   - Why calling `repository.save()` on a managed entity is redundant boilerplate.
   - First-Level Cache Identity Map; `flush()` vs `commit()`.
4. [The N+1 Query Problem, Fetch Types, and High-Performance JPA Queries](04_N_PLUS_ONE_PROBLEM_FETCH_TYPES_AND_EFFICIENT_QUERIES.md)
   - Why `FetchType.EAGER` is banned and does not fix N+1 queries.
   - Open Session in View (OSIV) connection exhaustion risk.
   - 3 Production solutions: `JOIN FETCH`, `@EntityGraph`, and Record DTO projections.
5. [Concurrency Control: Optimistic vs. Pessimistic Locking and Flash-Sale Inventory](05_CONCURRENCY_CONTROL_OPTIMISTIC_VS_PESSIMISTIC_LOCKING.md)
   - `@Version` optimistic locking column and handling `OptimisticLockingFailureException`.
   - `LockModeType.PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`) with lock timeouts.
   - High-throughput atomic single-statement SQL decrements for flash-sale stock reservation.
6. [Database Migrations with Flyway and Zero-Downtime Schema Evolution](06_DATABASE_MIGRATIONS_WITH_FLYWAY_AND_ZERO_DOWNTIME_SCHEMA_EVOLUTION.md)
   - Why `ddl-auto=update` is forbidden in production environments.
   - Flyway checksum validation, advisory locks, and script naming conventions.
   - The 5-Phase Expand-Contract (Parallel Run) pattern for non-breaking schema evolution.
