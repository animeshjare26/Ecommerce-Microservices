# Module 6: API Design and Resilience

> **Status:** 🟢 Complete (All 4 Deep-Dives Live)
>
> **Prerequisites:** Modules 1–5.
>
> **Outcome:** Design APIs that remain correct under retries, partial failure, and high traffic.

Read these lessons in order:

1. [Idempotency Keys and Preventing Duplicate Payments](01_IDEMPOTENCY_KEYS_AND_PAYMENT_DEDUPLICATION.md)
   - The three-way network timeout ambiguity and preventing double charges.
   - Redis 3-state state machine (`IN_PROGRESS`, `COMPLETED`) with atomic distributed locks.
   - Idempotency key payload tampering defense via SHA-256 request hashing.
2. [Timeouts, Retries, Exponential Backoff, and Jitter Algorithms](02_TIMEOUTS_RETRIES_EXPONENTIAL_BACKOFF_AND_JITTER.md)
   - Connect timeout vs. Read timeout: why infinite timeouts cause thread pool exhaustion.
   - The thundering herd problem and why naive exponential backoff causes synchronized shockwaves.
   - Full Jitter vs. Decorrelated Jitter algorithms and Google SRE Retry Budgets.
3. [Circuit Breakers, Bulkheads, and Graceful Degradation with Resilience4j](03_CIRCUIT_BREAKER_AND_BULKHEAD_WITH_RESILIENCE4J.md)
   - 3-State Finite State Machine (Closed, Open, Half-Open) with sliding windows.
   - Semaphore vs. Thread-Pool Bulkhead isolation mechanics.
   - Fallback execution and why Circuit Breakers must ignore 4xx client errors.
4. [RESTful API Design, RFC 7807 Problem Details, and Keyset Pagination at Scale](04_RESTFUL_API_DESIGN_ERROR_HANDLING_RFC7807_PAGINATION.md)
   - Standard HTTP status codes and noun-based hierarchical URI design.
   - RFC 7807 / RFC 9457 Problem Details for unified microservice error payloads.
   - Why Offset pagination collapses on large tables and how Keyset (cursor) pagination runs in $O(\log N)$.
