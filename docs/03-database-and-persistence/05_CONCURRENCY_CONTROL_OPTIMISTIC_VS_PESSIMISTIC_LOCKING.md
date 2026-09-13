# Deep Dive 05: Concurrency Control: Optimistic vs. Pessimistic Locking and Flash-Sale Inventory

> **Module:** `03-database-and-persistence`  
> **Target Audience:** Beginner Interns to Principal Architects / Tier-1 Interview Candidates  
> **Prerequisites:** Transactions and ACID (Deep Dive 02), JPA Entity Lifecycle (Deep Dive 03)  
> **Related Code in Project:** Inventory deduction in `product-service`, Order checkout in `order-service`  
> **Last Verified Against:** PostgreSQL 16 / Spring Boot 3.3.2 / Hibernate 6.5  

---

## 🗺️ Visual Reading Order & Navigation
```text
[04_N_PLUS_ONE_PROBLEM_FETCH_TYPES_AND_EFFICIENT_QUERIES.md]
                         │
                         ▼
[05_CONCURRENCY_CONTROL_OPTIMISTIC_VS_PESSIMISTIC_LOCKING.md]  ◄── YOU ARE HERE
                         │
                         ▼
[06_DATABASE_MIGRATIONS_WITH_FLYWAY_AND_ZERO_DOWNTIME_SCHEMA_EVOLUTION.md]
```

---

## 🟢 Tier 1: The Intuitive Mental Model

### The Airline Seat Reservation Analogy
Imagine Seat 14B on a flight from San Francisco to Tokyo:
1. **Optimistic Locking (First to Swipe Wins):**
   - 10 people are looking at Seat 14B on their phone screens simultaneously.
   - The airline does not lock the seat. Everyone can browse and input passenger details freely.
   - User A and User B both hit *"Pay Now"* at the exact same millisecond.
   - User A's payment reaches the database first. The seat's version increments: `v1 ──► v2`.
   - User B's transaction arrives 2 milliseconds later expecting `v1`. The database detects the version mismatch, rejects User B's update, and the app politely alerts: *"Sorry, this seat was just purchased by another traveler. Please pick another seat."*
   - **Characteristics:** Zero database locks held during customer browsing. Highest throughput when conflicts are rare.

2. **Pessimistic Locking (Physical Lock & Key):**
   - As soon as User A clicks on Seat 14B, the airline places a physical lock on that database row (**`SELECT FOR UPDATE`**).
   - If User B tries to view or click Seat 14B, their screen **freezes and waits** until User A either finishes payment or abandons the session!
   - **Characteristics:** Guarantees zero retries, but holds database locks and can cause massive queueing delays under high concurrency.

---

## 🟡 Tier 2: Optimistic Locking Deep-Dive

### Implementation with Spring Data JPA
Simply add the `@Version` annotation to your entity:

```java
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Integer stockQuantity;

    @Version // ◄── THE OPTIMISTIC LOCKING VERSION
    private Long version;
}
```

### What SQL Does Hibernate Actually Execute?
When you modify `product.setStockQuantity(product.getStockQuantity() - 1)`, Hibernate generates this exact SQL:

```sql
UPDATE products 
SET stock_quantity = 49, version = 6 
WHERE id = 101 AND version = 5;
```

**The Decision Logic:**
1. If the row was untouched by others, `version` in the DB is still `5`. The `UPDATE` modifies 1 row. Success!
2. If another concurrent transaction updated the product first, `version` in the DB is already `6`. The `WHERE id = 101 AND version = 5` matches **0 rows**!
3. Hibernate checks the JDBC update count: `if (rowsUpdated == 0)`.
4. It immediately throws: **`org.hibernate.StaleObjectStateException`** (wrapped by Spring as **`OptimisticLockingFailureException`**)!

---

## 🔴 Tier 3: Pessimistic Locking Deep-Dive (`SELECT ... FOR UPDATE`)

When 10,000 customers try to purchase 100 limited-edition sneakers in a **Black Friday Flash Sale**, Optimistic Locking collapses because 9,900 transactions will throw `OptimisticLockingFailureException` simultaneously and waste CPU retrying.

In high-contention scenarios, **Pessimistic Locking** is used:

```java
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithPessimisticLock(@Param("id") Long id);
}
```

**The Generated PostgreSQL SQL:**
```sql
SELECT * FROM products WHERE id = 101 FOR UPDATE;
```
- PostgreSQL places an **Exclusive Row Lock (`RowExclusiveLock`)** on row 101.
- Any subsequent transaction attempting to read with `FOR UPDATE` or modify row 101 is suspended by the database until the first transaction issues `COMMIT` or `ROLLBACK`.
- The `lock.timeout` of `3000ms` ensures that if a lock cannot be acquired within 3 seconds, it throws `LockTimeoutException` instead of hanging the HTTP thread indefinitely!

---

## ⚡ The Senior Architect Alternative: Atomic Single-Statement Decrements

Can we decrement inventory safely **without any `@Version` or `@Lock` annotations**?  
**YES! Using an Atomic Database Predicate:**

```java
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Modifying
    @Query("""
        UPDATE Product p 
        SET p.stockQuantity = p.stockQuantity - :quantity 
        WHERE p.id = :id AND p.stockQuantity >= :quantity
    """)
    int decrementStock(@Param("id") Long id, @Param("quantity") int quantity);
}
```

### Why this is the Fastest Production Pattern:
1. **Zero Application-Level Locks:** No `SELECT FOR UPDATE` lock duration.
2. **Guaranteed Consistency:** Relational databases execute single `UPDATE` statements atomically at the storage engine level.
3. **Built-in Guard:** The condition `AND p.stockQuantity >= :quantity` prevents stock from ever dropping below zero!
4. **Instant Verification:**
   ```java
   int rowsUpdated = productRepository.decrementStock(productId, 1);
   if (rowsUpdated == 0) {
       throw new InsufficientStockException("Sold out!");
   }
   ```

---

## 🏆 Tier 4: Top 5 Tricky Tier-1 Interview Questions & Answers

### Q1: What causes a Database Deadlock during concurrent updates, and how do you prevent it?
**The Classic Deadlock Scenario:**
- Transaction 1: Updates Product A (locks A), then tries to update Product B.
- Transaction 2: Updates Product B (locks B), then tries to update Product A.
- **Deadlock!** Both transactions wait on each other forever until PostgreSQL's `deadlock_timeout` detects the cycle and aborts one transaction with error `40P01: deadlock detected`.

**How to Prevent Deadlocks Everywhere in Code:**
> "Enforce a **Strict Global Resource Ordering**:
> Whenever an order or batch updates multiple items (e.g., reserving stock for products 5, 2, and 9), always **sort the product IDs in ascending order** before acquiring locks or executing updates:
> `List<Long> sortedIds = itemIds.stream().sorted().toList();`
> Because every transaction in the entire company acquires locks in the identical order (ID 2 ──► ID 5 ──► ID 9), a circular wait condition is mathematically impossible!"

---

### Q2: How does Spring Boot retry an operation when an `OptimisticLockingFailureException` occurs?
**High-Scoring Answer:**
> "We use **Spring Retry** (`@Retryable`):
> ```java
> @Service
> public class ProductService {
>     @Retryable(
>         retryFor = { OptimisticLockingFailureException.class },
>         maxAttempts = 3,
>         backoff = @Backoff(delay = 50, multiplier = 2.0)
>     )
>     @Transactional
>     public void deductStock(Long productId, int quantity) {
>         Product product = productRepository.findById(productId).orElseThrow();
>         product.setStockQuantity(product.getStockQuantity() - 1);
>     }
> }
> ```
> If a version collision occurs, Spring automatically catches the exception, waits 50ms (exponential backoff), re-opens a fresh transaction, reads the latest version from the database, and retries the update."

---

### Q3: What is the difference between `PESSIMISTIC_READ` and `PESSIMISTIC_WRITE`?
**High-Scoring Answer:**
> - **`PESSIMISTIC_READ` (SQL: `FOR SHARE`):** Acquires a shared lock. Multiple transactions can acquire `FOR SHARE` simultaneously to read data and prevent other transactions from updating or deleting the row until they finish.
> - **`PESSIMISTIC_WRITE` (SQL: `FOR UPDATE`):** Acquires an exclusive lock. Only ONE transaction can hold the lock. No other transaction can update, delete, or acquire a lock on this row until the transaction commits."

---

### Q4: Why is a Redis Distributed Lock preferred over Database Pessimistic Locking in multi-instance microservices?
**High-Scoring Answer:**
> "1. **Database Connection Pool Exhaustion:** Database locks require keeping a physical HikariCP database connection held open across the entire lock duration. Holding 1,000 DB connections will crash PostgreSQL.
> 2. **Cross-Service Coordination:** A database lock in `product_db` cannot coordinate actions that span multiple independent microservices (e.g. reserving inventory in Product Service while debiting a wallet in Payment Service).
> 3. A **Redis Distributed Lock (via Redisson)** operates in-memory in microseconds without consuming database connections."

---

### Q5: Can the `@Version` property in JPA be a `Timestamp` instead of an `Integer` or `Long`?
**High-Scoring Answer:**
> "Yes, JPA supports `java.sql.Timestamp` or `java.time.Instant` as `@Version` fields. However, using timestamps is **strongly discouraged in high-throughput systems**:
> 1. JVM system clocks and OS timers have microsecond/millisecond resolution limits. Two transactions committing in rapid succession on multi-core CPUs may record the exact same timestamp, causing the version check to miss concurrent modifications.
> 2. Integer/Long counters increment strictly monotonically ($1 \to 2 \to 3$), guaranteeing 100% collision detection accuracy."
