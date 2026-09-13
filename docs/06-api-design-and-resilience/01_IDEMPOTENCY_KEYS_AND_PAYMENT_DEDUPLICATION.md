# Deep Dive 01: Idempotency Keys and Preventing Duplicate Payments

> **Module:** `06-api-design-and-resilience`  
> **Target Audience:** Beginner Interns to Principal Payment Engineers / Tier-1 Candidates  
> **Prerequisites:** HTTP Status Codes, Redis Basics, Distributed Systems  
> **Related Code in Project:** Payment checkout endpoints, Order creation, API Gateway headers  
> **Last Verified Against:** Spring Boot 3.3.2 / Redis 7.2 / Java 21  

---

## 🗺️ Visual Reading Order & Navigation
```text
[05-security-and-identity/05_REFRESH_TOKEN_ROTATION_AND_BCRYPT_MASTERCLASS.md]
                                   │
                                   ▼
[01_IDEMPOTENCY_KEYS_AND_PAYMENT_DEDUPLICATION.md]  ◄── YOU ARE HERE
                                   │
                                   ▼
[02_TIMEOUTS_RETRIES_EXPONENTIAL_BACKOFF_AND_JITTER.md]
```

---

## 🟢 Tier 1: The Intuitive Mental Model

### The Vending Machine & Network Black Hole Analogy
Imagine putting a $10 bill into a vending machine for an espresso:
1. You press the button. The machine brews the coffee and dispenses the cup.
2. But before the display screen says *"Enjoy your coffee!"*, the machine's power cable is momentarily tripped!
3. **The Customer's Dilemma:** You don't know if your $10 was accepted, if coffee is brewing, or if it failed completely!
4. If you press the button again without a unique receipt number, the machine takes another $10 and charges you twice!
5. **The Idempotency Key Solution:**
   - Before pressing the button, you write down a unique UUID on a physical token: `TICKET-7890`.
   - You hand the token to the machine.
   - If the network cuts out and your phone retries 3 seconds later with the **exact same token `TICKET-7890`**, the machine checks its log: *"Wait! I already brewed coffee for `TICKET-7890`! Here is your receipt and coffee, I will NOT charge you again!"*

```text
Client (Mobile App)                    API Gateway / Payment Service                   Redis Cache
        │                                            │                                     │
        │── POST /orders (Idempotency-Key: abc-123) ─►│                                     │
        │                                            │── SETNX "idemp:abc-123" "IN_FLIGHT" ─►│
        │                                            │◄── 1 (Lock Acquired Successfully) ──│
        │                                            │                                     │
        │                                            │── Processes Payment with Stripe...  │
        │                                            │── Saves Order in PostgreSQL...      │
        │                                            │                                     │
        │                                            │── SET "idemp:abc-123" {JSON Response}►│
        │◄── 201 Created (Order #9001) ──────────────│                                     │
        │                                            │                                     │
        │ [NETWORK TIMEOUT OR ACCIDENTAL DOUBLE-TAP] │                                     │
        │── POST /orders (Idempotency-Key: abc-123) ─►│                                     │
        │                                            │── GET "idemp:abc-123" ──────────────►│
        │                                            │◄── Returns {JSON Response} ─────────│
        │◄── 200 OK (Cached Order #9001 Payload) ────│ (Zero double charge! Zero DB writes!)
```

---

## 🟡 Tier 2: The Two Phase State Machine in Redis

A common mistake made by junior developers is saving the idempotency key *only after* the payment succeeds.  
**What happens if the customer double-clicks within 50 milliseconds?** Both requests reach the server simultaneously before either has finished saving to Redis!

To prevent concurrent race conditions, an Idempotency Service must implement a **3-State Lock State Machine** using atomic Redis commands:

### State 1: `IN_PROGRESS` (Distributed Lock)
When the request arrives, execute an atomic Redis `SET`:
```bash
SET idemp:abc-123 "IN_PROGRESS" EX 120 NX
```
- `NX`: Set only if key does **NOT** exist.
- `EX 120`: 2-minute safety TTL (if the server crashes mid-payment, the lock auto-releases).
- If Redis returns `nil`, another thread is currently processing this exact payment! The server immediately returns **`HTTP 409 Conflict`** with message: *"Transaction in progress. Please do not retry."*

### State 2: `COMPLETED` (Cached Response)
Once the database transaction commits and Stripe responds:
```bash
SET idemp:abc-123 '{"orderId":"9001","status":"PAID"}' EX 86400
```
- Overwrites the key with the final HTTP response status code and JSON payload.
- Retained for 24 hours (`EX 86400`).
- Any duplicate retry within 24 hours immediately returns the exact saved JSON response with an extra header: `X-Cache-Lookup: IDEMPOTENT-HIT`.

---

## 🔴 Tier 3: Low-Level Internal Mechanics: The Three-Way Network Ambiguity

Why can we never assume a timed-out HTTP request failed?  
Every network call over TCP/IP involves **Three Distinct Failure Zones**:

```text
[ Client ] ──────── (1) Request Packet Lost in Transit ────────► [ Server ]
[ Client ] ──────── (2) Server Crashed Mid-Execution  ─────────► [ Server ]
[ Client ] ◄─────── (3) Response Packet Lost in Transit ──────── [ Server ]
```

When a client socket throws `SocketTimeoutException`:
- In Case 1: The server never saw the request (Safe to retry).
- In Case 2: The database was partially modified (Uncertain state).
- In Case 3: **The payment was 100% processed and money was deducted**, but the client never received the confirmation packet!
- If the client retries without an Idempotency Key, **Case 3 guarantees a duplicate charge!**

---

## 🏆 Tier 4: Top 5 Tricky Tier-1 Interview Questions & Answers

### Q1: What HTTP status code should you return when a client sends an Idempotency Key that is currently being processed by another thread?
**High-Scoring Answer:**
> "Return **`409 Conflict`** (or `425 Too Early` / `429 Too Many Requests`).
> 
> The response body should explain:
> ```json
> {
>   "statusCode": 409,
>   "message": "A request with this Idempotency-Key is currently in-flight. Please wait before retrying."
> }
> ```
> This prevents aggressive clients from hammering the payment processor while the first transaction is awaiting third-party webhook confirmation."

---

### Q2: What happens if a client sends the same `Idempotency-Key` with a completely DIFFERENT request payload (e.g. changing the amount from $10 to $1,000)?
**High-Scoring Answer:**
> "This is a serious security vulnerability and developer error called **Idempotency Key Mismatch / Payload Tampering**.
> 
> **How to defend:**
> When storing the idempotency record in Redis, compute and store a cryptographic hash (SHA-256) of the initial HTTP request body:
> `payload_hash = SHA256(request.getBody())`
> 
> When a retry arrives:
> 1. Fetch the stored record from Redis.
> 2. Calculate `SHA256(currentRequest.getBody())`.
> 3. If the hashes do not match, **reject immediately with `HTTP 422 Unprocessable Entity` or `HTTP 400 Bad Request`**:
>    `"Idempotency-Key was previously used with a different request payload!"`"

---

### Q3: How do you handle idempotency at the Database layer without Redis?
**High-Scoring Answer:**
> "By creating an **`idempotency_keys` table** in PostgreSQL with a unique constraint:
> ```sql
> CREATE TABLE idempotency_records (
>     key VARCHAR(128) PRIMARY KEY,
>     user_id BIGINT NOT NULL,
>     response_payload JSONB,
>     status VARCHAR(32) NOT NULL,
>     created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
> );
> ```
> Inside the `@Transactional` payment method:
> 1. Execute: `INSERT INTO idempotency_records (key, user_id, status) VALUES (?, ?, 'IN_PROGRESS')`.
> 2. If PostgreSQL throws a unique constraint violation (`23505: unique_violation`), catch it and return either the existing response or `409 Conflict`.
> 3. This guarantees ACID transactional atomicity between the idempotency record and the business order creation in the same database engine."

---

### Q4: Why are HTTP `GET`, `PUT`, and `DELETE` considered naturally idempotent, while `POST` is not?
| HTTP Method | Naturally Idempotent? | Reason |
| :--- | :---: | :--- |
| **`GET`** | ✅ Yes | Safe read-only operation; reading 1 time or 100 times produces identical server state. |
| **`PUT`** | ✅ Yes | Replaces the entire target resource with the provided payload (`UPDATE users SET name='Alice' WHERE id=1`). Executing 10 times results in the same final state. |
| **`DELETE`** | ✅ Yes | Deleting resource #5 once removes it. Deleting it again returns 404, but the final state on the server is identical (resource #5 is gone). |
| **`POST`** | ❌ **NO** | Appends new records (`INSERT INTO orders`). Calling `POST /orders` 5 times creates 5 separate database rows unless guarded by an Idempotency Key. |

---

### Q5: How do you clean up expired Idempotency Keys in production?
**High-Scoring Answer:**
> "1. **In Redis:** Use native Redis TTLs (`EX 86400`). Redis automatically evicts expired keys in the background via probabilistic passive and active expiration loops.
> 2. **In PostgreSQL:** Run a scheduled background cron job (or `pg_cron`) during off-peak hours:
>    `DELETE FROM idempotency_records WHERE created_at < NOW() - INTERVAL '7 days';`
>    Partitioning the `idempotency_records` table by day (`RANGE (created_at)`) allows dropping entire partitions instantly via `DROP TABLE` without generating slow `DELETE` WAL entries."
