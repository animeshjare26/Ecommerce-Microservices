# Deep Dive 01: Database Indexing Internals: B-Trees, Composite Indexes, and EXPLAIN ANALYZE

> **Module:** `03-database-and-persistence`  
> **Target Audience:** Beginner Interns to Senior Database Engineers / Tier-1 Candidates  
> **Prerequisites:** SQL Basics, Binary Search Trees  
> **Related Code in Project:** PostgreSQL database schemas, indexes on `users.email`, `products.sku`, `orders.user_id`  
> **Last Verified Against:** PostgreSQL 16 / Java 21 / Spring Data JPA 3.3  

---

## 🗺️ Visual Reading Order & Navigation
```text
[02-spring-boot-foundations/04_SPRING_TRANSACTION_MANAGEMENT_PROXIES_AND_SELF_INVOCATION.md]
                                   │
                                   ▼
[01_INDEXING_INTERNALS_B_TREES_AND_EXPLAIN_ANALYZE.md]  ◄── YOU ARE HERE
                                   │
                                   ▼
[02_TRANSACTIONS_ACID_AND_ISOLATION_LEVELS.md]
```

---

## 🟢 Tier 1: The Intuitive Mental Model

### The Textbook Index Analogy
Imagine a 1,200-page medical textbook:
- **Without an Index (Sequential Scan / Table Scan):**
  If you want to find information on *"Hypertension"*, you must read all 1,200 pages from page 1 to page 1,200. If your table has 50,000,000 rows, this takes 45 seconds of heavy disk I/O.
- **With a B-Tree Index:**
  You flip to the back of the book where topics are sorted alphabetically. You jump to the letter **"H"**, find *"Hypertension: pages 312, 450, 891"*, and jump directly to page 312 in 2 milliseconds!

```text
                                [ Root Node: 50 | 100 ]
                                     /      |      \
                                    /       |       \
               [ Branch: 20 | 35 ]   [ Branch: 70 | 85 ]   [ Branch: 120 | 150 ]
                   /     |    \
                  /      |     \
               [ Leaf: 1..19 ] ──► [ Leaf: 20..34 ] ──► [ Leaf: 35..49 ]
                 (Holds direct physical pointers to PostgreSQL disk pages / ctid)
```

---

## 🟡 Tier 2: Common Doubts, Gotchas & Anti-Patterns

### 1. The Leftmost Prefix Rule for Composite Indexes
**The Trap:**
You create a composite index on two columns:
```sql
CREATE INDEX idx_orders_user_status ON orders (user_id, status);
```
Now look at these three queries:
- **Query A:** `SELECT * FROM orders WHERE user_id = 50 AND status = 'PENDING';`  
  ──► 🟢 **Fast Index Scan!** Uses both columns.
- **Query B:** `SELECT * FROM orders WHERE user_id = 50;`  
  ──► 🟢 **Fast Index Scan!** Uses the leftmost column (`user_id`).
- **Query C:** `SELECT * FROM orders WHERE status = 'PENDING';`  
  ──► 🔴 **SLOW SEQUENTIAL SCAN!** The index is completely ignored!

**Why does Query C ignore the index?**
A composite index is like a telephone directory sorted by `(LastName, FirstName)`:
- If you search for *"Smith, John"*, you flip to "Smith" and find "John".
- If you search for *"Smith"*, you flip to "Smith".
- But if you search for *"Everyone whose first name is John"* without knowing their last name, the directory order is useless! You must read the phone book from cover to cover!

---

### 2. "Why didn't PostgreSQL use my index even though an index exists?"
Junior developers often panic when they see `Seq Scan` on an indexed column. PostgreSQL's Cost-Based Optimizer (CBO) will deliberately skip an index in these 3 common scenarios:
1. **Low Cardinality / High Selectivity:** If a query like `WHERE is_active = true` matches 80% of the table rows, jumping back and forth between the index and disk pages (random I/O) is much slower than simply reading the table sequentially from start to finish (sequential I/O).
2. **Applying Functions on Indexed Columns:**
   ```sql
   -- BAD: Bypasses the index on 'email'!
   SELECT * FROM users WHERE LOWER(email) = 'john@example.com';
   
   -- FIX: Use a Function-Based Index:
   CREATE INDEX idx_users_lower_email ON users (LOWER(email));
   ```
3. **Data Type Mismatches (Implicit Casts):** If `sku` is a `VARCHAR`, but you query `WHERE sku = 12345` (integer), PostgreSQL casts every row in the table via `CAST(sku AS integer)`, preventing index usage!

---

## 🔴 Tier 3: Low-Level Internal Mechanics: Reading `EXPLAIN (ANALYZE, BUFFERS)`

When tuning database performance, never guess. Run:
```sql
EXPLAIN (ANALYZE, BUFFERS) 
SELECT * FROM orders WHERE user_id = 42 ORDER BY created_at DESC LIMIT 10;
```

### The 3 Types of Index Scans in PostgreSQL

1. **Index Scan:**
   - Reads the B-Tree index to find row pointers (`ctid`), then visits the table heap to fetch the column data.
2. **Index Only Scan (The Holy Grail):**
   - If the index contains **every single column** requested in the `SELECT` clause (e.g. `CREATE INDEX idx ON orders (user_id, total_amount)`), PostgreSQL reads the data **directly from the index in RAM without ever touching the table on disk**!
3. **Bitmap Index Scan:**
   - When a query matches many rows, PostgreSQL scans the index and builds a bitmap in memory of all matching page numbers. It then sorts the page numbers physically and reads each disk page in sequential order, converting random I/O into sequential I/O.

---

## 🏆 Tier 4: Top 5 Tricky Tier-1 Interview Questions & Answers

### Q1: Why do relational databases use B-Trees instead of Binary Search Trees (BST) or Hash Maps for indexes?
**High-Scoring Answer:**
> "1. **Versus Hash Maps:** Hash indexes only support equality lookups ($O(1)$ `WHERE id = 5`). They cannot support range queries (`WHERE price BETWEEN 10 AND 50`), prefix matching (`LIKE 'ABC%'`), or `ORDER BY` sorting, all of which B-Trees handle naturally in $O(\log N)$.
> 2. **Versus Binary Search Trees:** A binary tree has at most 2 children per node, making it very deep ($h \approx \log_2 N$). For 10,000,000 rows, depth is ~24, requiring 24 separate random disk seeks.
> In contrast, a **B-Tree has a high branching factor (fan-out of 100–500 keys per node)** matching the 8KB physical OS/disk page size. A B-Tree indexing 10,000,000 rows has a height of only **3 to 4 levels**, requiring at most 3 or 4 disk I/O operations (the top 2 levels of which are almost always cached in RAM buffer pools)."

---

### Q2: What is the cost of adding too many indexes to a database table?
**High-Scoring Answer:**
> "While indexes dramatically accelerate `SELECT` reads, every additional index imposes heavy write penalties:
> 1. **Write Amplification:** Every `INSERT`, `DELETE`, and indexed `UPDATE` must synchronously update the table heap **plus all secondary B-Trees**, increasing disk I/O and transaction lock duration.
> 2. **B-Tree Page Splits:** When an 8KB index leaf node becomes full, inserting a new key forces the database to allocate a new page, split the keys 50/50, and update parent pointers, incurring write latency spikes.
> 3. **Buffer Pool Pollution:** Excess indexes consume valuable RAM in PostgreSQL's `shared_buffers`, evicting hot data pages."

---

### Q3: What is a Covering Index, and how does the `INCLUDE` clause work in PostgreSQL?
**High-Scoring Answer:**
> "A Covering Index is an index that satisfies an entire query without inspecting the underlying table heap, enabling an **Index Only Scan**.
> 
> In PostgreSQL 11+, we use the **`INCLUDE` clause**:
> ```sql
> CREATE INDEX idx_users_email_covering ON users (email) INCLUDE (first_name, role);
> ```
> - The column `email` is placed in the B-Tree search keys to support fast lookups.
> - The columns `first_name` and `role` are stored **only at the leaf nodes** as payload data. They do not participate in B-Tree sorting or branching, keeping the index tree compact while eliminating heap lookups."

---

### Q4: What is a Partial Index, and when should it be used?
**High-Scoring Answer:**
> "A Partial Index indexes only a subset of rows that satisfy a specific `WHERE` predicate:
> ```sql
> CREATE INDEX idx_orders_unprocessed ON orders (created_at) WHERE status = 'PENDING';
> ```
> In an e-commerce database with 100,000,000 orders, 99.9% are in `DELIVERED` or `CANCELLED` status, while only 5,000 are `PENDING`. A regular index on `status` would consume gigabytes of RAM. A partial index indexes only the 5,000 pending orders, producing a tiny index (< 100KB) that lives permanently in CPU L3 cache and runs orders of magnitude faster."

---

### Q5: How do you optimize slow `LIKE '%query%'` wildcard searches in PostgreSQL?
**High-Scoring Answer:**
> "Standard B-Tree indexes can only optimize prefix wildcards (`LIKE 'prefix%'`) using the leftmost index property. Suffix and infix wildcards (`LIKE '%query%'`) force a full sequential table scan.
> 
> **Solution:** Use a **Trigram (GIN) Index** via the `pg_trgm` extension:
> ```sql
> CREATE EXTENSION IF NOT EXISTS pg_trgm;
> CREATE INDEX idx_products_name_trgm ON products USING gin (name gin_trgm_ops);
> ```
> This breaks words into 3-character slices (trigrams) and stores them in an Inverted Index (GIN), allowing lightning-fast substring searches and fuzzy matching."
