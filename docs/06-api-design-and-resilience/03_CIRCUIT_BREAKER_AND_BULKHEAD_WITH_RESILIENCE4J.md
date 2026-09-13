# Deep Dive 03: Circuit Breakers, Bulkheads, and Graceful Degradation with Resilience4j

> **Module:** `06-api-design-and-resilience`  
> **Target Audience:** Beginner Interns to Staff Distributed Systems Architects / Tier-1 Candidates  
> **Prerequisites:** Timeouts & Retries (Deep Dive 02), Java Concurrency (Module 01)  
> **Related Code in Project:** API Gateway circuit breaker filters, Feign clients, Fallback controllers  
> **Last Verified Against:** Resilience4j 2.2 / Spring Boot 3.3.2 / Spring Cloud 2023.0  

---

## 🗺️ Visual Reading Order & Navigation
```text
[02_TIMEOUTS_RETRIES_EXPONENTIAL_BACKOFF_AND_JITTER.md]
                         │
                         ▼
[03_CIRCUIT_BREAKER_AND_BULKHEAD_WITH_RESILIENCE4J.md]  ◄── YOU ARE HERE
                         │
                         ▼
[04_RESTFUL_API_DESIGN_ERROR_HANDLING_RFC7807_PAGINATION.md]
```

---

## 🟢 Tier 1: The Intuitive Mental Model

### 1. The Home Electrical Circuit Breaker
In your home, if the toaster's wiring catches fire and draws 50 Amps through the wall, the **electrical circuit breaker automatically trips OPEN**.
It instantly cuts power to the kitchen.
- Why? It doesn't fix the toaster. It prevents the entire house from burning to the ground!
- In microservices: If the `recommendation-service` crashes, the Circuit Breaker trips OPEN. Instead of letting 10,000 requests hang and consume all server memory, the Gateway immediately responds with a fast fallback: `[]` (empty recommendations) in 1 millisecond!

### 2. The Ship Watertight Bulkhead
A submarine or cargo ship is divided into **separate watertight compartments (Bulkheads)**.
If an iceberg punctures Compartment #3, water floods that compartment only. The heavy watertight doors seal it off, and the remaining 9 compartments stay dry. The ship stays afloat!
- In microservices: If the slow `analytics-service` is consuming threads, a **Bulkhead isolates its thread pool** so it cannot steal worker threads from the critical `payment-service`!

```text
The Circuit Breaker 3-State Finite State Machine:

              ┌──────────────────────────────────────────────┐
              │                                              │
              ▼                                              │ Success Rate > Threshold
     ┌──────────────────┐    Failure Rate > 50%    ┌──────────────────┐
     │      CLOSED      │ ───────────────────────► │       OPEN       │
     │ (Calls Allowed)  │                          │  (Fast-Fail All) │
     └──────────────────┘                          └────────┬─────────┘
              ▲                                             │
              │                                             │ Wait Duration Elapsed
              │              ┌──────────────────┐           │ (e.g. 10s cooldown)
              │              │    HALF-OPEN     │ ◄─────────┘
              └───────────── │  (Trial Probes)  │
      Trial Successes        └──────────────────┘
```

---

## 🟡 Tier 2: The Circuit Breaker States Explained

### 1. CLOSED State (Normal Operation)
- All requests are permitted to reach downstream services.
- Resilience4j records the outcome of calls in a **Sliding Window** (e.g. last 100 requests).
- Both **Exceptions** and **Slow Calls** (calls exceeding `slowCallDurationThreshold`) are counted as failures.
- If the failure rate stays below the threshold (e.g. `< 50%`), the circuit remains CLOSED.

### 2. OPEN State (Tripped / Fast-Fail)
- When failure rate reaches $\ge 50\%$, the breaker trips OPEN.
- **Zero network calls are transmitted over the wire!**
- All incoming requests immediately throw **`CallNotPermittedException`** (or invoke the configured fallback method) in **0.1 milliseconds**!
- This gives the struggling downstream database or microservice room to recover without being hammered by incoming traffic.

### 3. HALF-OPEN State (Trial Probing)
- After a configured cooldown period (e.g. `waitDurationInOpenState = 10s`), the breaker transitions to HALF-OPEN.
- It permits a small sample of trial requests (e.g. `permittedNumberOfCallsInHalfOpenState = 10`) to reach the downstream service.
- If the trial requests succeed, the breaker transitions back to **CLOSED**.
- If even one trial request fails, the breaker immediately snaps back to **OPEN** for another 10 seconds.

---

## 🔴 Tier 3: The Bulkhead Pattern: Semaphore vs. ThreadPool

How do you prevent one non-critical slow service from starving the entire application?

```text
[ Incoming Web Traffic ]
          │
          ├── (80 Threads Dedicated) ──► [ Payment Service Bulkhead ]
          │
          └── (10 Threads Dedicated) ──► [ Recommendation Bulkhead (Capped!) ]
```

| Dimension | Semaphore Bulkhead | ThreadPool Bulkhead |
| :--- | :--- | :--- |
| **Mechanism** | Uses a Java `AtomicInteger` / `Semaphore` counter. | Uses an isolated, dedicated `ThreadPoolExecutor` and queue. |
| **Thread Context** | Executes on the **current caller thread** (Zero context-switching overhead). | Executes on a **separate background thread** (Incurs thread context switch). |
| **ThreadLocal / MDC** | Works natively with `SecurityContext` and SLF4J MDC without extra propagation. | Requires explicit `SecurityContext` propagation to background threads. |
| **Timeout Support** | Cannot preemptively cancel a frozen method call. | Can interrupt and cancel tasks on timeout via `Future.cancel(true)`. |
| **Best Used For** | Non-blocking reactive calls (Spring WebFlux / Netty / Gateway). | Blocking I/O calls (Spring MVC / JDBC / legacy REST clients). |

---

## 💻 Spring Boot Configuration with Resilience4j

```yaml
resilience4j.circuitbreaker:
  instances:
    productService:
      slidingWindowType: COUNT_BASED
      slidingWindowSize: 50
      minimumNumberOfCalls: 20
      failureRateThreshold: 50.0
      slowCallRateThreshold: 50.0
      slowCallDurationThreshold: 2000ms
      waitDurationInOpenState: 10000ms
      permittedNumberOfCallsInHalfOpenState: 5
      automaticTransitionFromOpenToHalfOpenEnabled: true
```

### Implementing Fallbacks in Java:
```java
@Service
public class CatalogClient {

    @CircuitBreaker(name = "productService", fallbackMethod = "getCachedProducts")
    public List<ProductDto> getFeaturedProducts() {
        return restClient.get()
            .uri("http://product-service/api/v1/products/featured")
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    }

    // Fallback signature MUST match original method + Throwable as last argument!
    public List<ProductDto> getCachedProducts(Throwable ex) {
        log.warn("Product service down! Falling back to Redis cache. Reason: {}", ex.getMessage());
        return redisCache.get("fallback:featured_products");
    }
}
```

---

## 🏆 Tier 4: Top 5 Tricky Tier-1 Interview Questions & Answers

### Q1: What is the difference between a Count-Based Sliding Window and a Time-Based Sliding Window in Resilience4j?
**High-Scoring Answer:**
> - **Count-Based Sliding Window:** Uses a circular array of $N$ elements (e.g. last 100 requests). It records the outcome of the last 100 calls regardless of how long ago they occurred. Best for high-frequency, steady-traffic APIs.
> - **Time-Based Sliding Window:** Uses an epoch array of $N$ seconds (e.g. last 60 seconds). Only calls that completed within the last 60 seconds count towards the failure rate calculation; older calls automatically roll off the window. Best for low-frequency or bursty microservices where failures from 3 hours ago should not trip the breaker today."

---

### Q2: Why is the `minimumNumberOfCalls` parameter critical in Circuit Breakers?
**High-Scoring Answer:**
> "If `minimumNumberOfCalls` is not configured or set to 1:
> Suppose the application boots and receives only 2 requests. If the very first request fails due to a transient DNS blip, the failure rate is immediately **1 out of 1 = 100%**!
> The Circuit Breaker trips OPEN on the very first request of the day, locking out all users!
> 
> Setting `minimumNumberOfCalls = 20` prevents premature tripping: Resilience4j will **not** calculate failure rates until at least 20 calls have been recorded in the window."

---

### Q3: Why did Netflix deprecate Hystrix in favor of Resilience4j?
**High-Scoring Answer:**
> "1. **Architecture & Overhead:** Hystrix was designed around heavy thread-pool isolation per command, creating massive CPU context-switching overhead and thread pool allocation costs.
> 2. **Modern Java:** Resilience4j is built from the ground up using **Java 8 functional interfaces (`Predicate`, `Supplier`)** and lightweight Java decorators, allowing CircuitBreaker, RateLimiter, Bulkhead, and Retry to be stacked cleanly on any function:
>    `Supplier<String> decorated = CircuitBreaker.decorateSupplier(cb, Retry.decorateSupplier(retry, target));`
> 3. **Reactive Support:** Resilience4j provides native operators for Project Reactor (`Mono` and `Flux`), making it compatible with non-blocking Spring Cloud Gateway and WebFlux without thread blocking."

---

### Q4: What exceptions should a Circuit Breaker IGNORE, and why?
**High-Scoring Answer:**
> "A Circuit Breaker should **ignore 4xx client errors** (such as `IllegalArgumentException`, `MethodArgumentNotValidException`, `ResourceNotFoundException` - 404, or `BadCredentialsException` - 401).
> 
> **Why?**
> A user typing the wrong password or passing an invalid email address is **expected client behavior**, not a sign that the service is unhealthy or crashing. If client validation errors counted as failures, an attacker could deliberately send 100 invalid payloads to trip the circuit breaker and take down the entire service for all legitimate users (Denial of Service attack)!"

---

### Q5: How does a Circuit Breaker behave in a Reactive Gateway (Spring Cloud Gateway)?
**High-Scoring Answer:**
> "In Spring Cloud Gateway, the circuit breaker operates as a reactive filter (`SpringCloudCircuitBreakerFilterFactory`):
> 1. It wraps downstream `WebClient` / Netty HTTP dispatches in a Reactive Circuit Breaker.
> 2. If the downstream microservice times out or returns HTTP 500s exceeding the threshold, the reactive pipeline redirects the `ServerWebExchange` to a local fallback URI (e.g. `forward:/fallback/products`).
> 3. Because it uses Netty event-loop non-blocking timers instead of blocking threads, a single Gateway instance can manage circuit breakers across 100,000 concurrent client streams with minimal memory overhead."
