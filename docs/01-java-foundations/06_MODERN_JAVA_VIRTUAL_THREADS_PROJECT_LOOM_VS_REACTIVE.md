# Deep Dive 06: Modern Java: Virtual Threads (Project Loom) vs. Reactive Netty, Records & Pattern Matching

> **Module:** `01-java-foundations`  
> **Target Audience:** Beginner Interns to Principal Architects / Tier-1 Interview Candidates  
> **Prerequisites:** Java Threads, Netty Event Loops (Module 07), Reactive Programming  
> **Related Code in Project:** `api-gateway` (WebFlux/Netty) vs Downstream MVC microservices  
> **Last Verified Against:** Java 21 LTS / Spring Boot 3.3.2  

---

## 🗺️ Visual Reading Order & Navigation
```text
[05_JVM_MEMORY_MODEL_GC_ALGORITHMS_AND_OOM_ANALYSIS.md]
              │
              ▼
[06_MODERN_JAVA_VIRTUAL_THREADS_PROJECT_LOOM_VS_REACTIVE.md]  ◄── YOU ARE HERE
              │
              ▼
[02-spring-boot-foundations/README.md]
```

---

## 🟢 Tier 1: The Intuitive Mental Model

### The Heavy Excavator vs. The Swarm of Workers
1. **Platform Threads (Classic 1:1 OS Thread Model):**
   - Each Java thread is a 1-ton diesel excavator mapped 1:1 to an operating system kernel thread.
   - It requires **1 MB of reserved memory** just for its stack.
   - If the driver stops digging because they are waiting for a telephone call from a supplier (blocking network I/O, database query), the 1-ton machine sits idle, consuming memory and fuel.
   - Your computer can only operate ~1,000–2,000 of these before running out of RAM and crashing the OS kernel scheduler.

2. **Virtual Threads (Project Loom in Java 21):**
   - You have only **8–16 heavy excavators (Carrier OS Threads)**, matched exactly to your physical CPU cores.
   - But you have **1,000,000 lightweight worker badges (Virtual Threads)** that cost almost zero RAM (~few hundred bytes in heap).
   - When Worker #405 needs to wait for a database query, they step down from the excavator and pause in the breakroom (**Unmounting to Heap**).
   - Worker #406 immediately hops into the driver's seat and continues digging on the same CPU core.
   - When the database query finally responds, Worker #405 hops back into any free excavator (**Mounting back to Carrier Thread**).

```text
┌────────────────────────────────────────────────────────────────────────┐
│                   VIRTUAL THREADS (1,000,000+)                         │
│  [VT 1: Sleep]   [VT 2: DB Wait]   [VT 3: Active]   [VT 4: Active] ... │
└──────────┬──────────────┬───────────────┬───────────────┬──────────────┘
           │ (Unmounted)  │ (Unmounted)   │ (Mounted)     │ (Mounted)
           ▼              ▼               ▼               ▼
┌────────────────────────────────────────────────────────────────────────┐
│             CARRIER THREADS (ForkJoinPool: 8 OS Threads)               │
│               [ Carrier-1 ]                  [ Carrier-2 ]             │
└─────────────────────┬──────────────────────────────┬───────────────────┘
                      ▼                              ▼
              [ CPU Core 0 ]                  [ CPU Core 1 ]
```

---

## 🟡 Tier 2: Common Doubts, Gotchas & Anti-Patterns

### 1. The Deadly "Thread Pinning" Bug
**The Problem:**
If a Virtual Thread encounters a blocking I/O call while executing inside a `synchronized` block or calling native C code (JNI), the JVM **cannot unmount** the virtual thread!
It stays **pinned** to the Carrier Thread:
```java
public class OrderService {
    // DISASTER WITH VIRTUAL THREADS:
    public synchronized void processPayment(String orderId) {
        // Blocking HTTP call to Stripe API while holding monitor lock!
        restTemplate.postForObject("https://api.stripe.com/pay", ...);
    }
}
```
**Why this destroys throughput:**
- While waiting 300ms for Stripe's HTTP response, the Virtual Thread cannot unmount.
- The underlying Carrier OS thread is blocked and immobilized!
- If 16 requests hit this method simultaneously, **all 16 Carrier Threads become frozen**, starving the entire JVM and bringing the server to an absolute halt!

> [!CAUTION]
> **The Fix:** Replace `synchronized` blocks with `ReentrantLock`. `ReentrantLock` integrates with Loom's parking mechanics and **never pins** carrier threads:
> ```java
> private final ReentrantLock lock = new ReentrantLock();
> 
> public void processPayment(String orderId) {
>     lock.lock();
>     try {
>         restTemplate.postForObject("https://api.stripe.com/pay", ...);
>     } finally {
>         lock.unlock();
>     }
> }
> ```

---

### 2. "Should I pool Virtual Threads like `ThreadPoolExecutor`?"
**NO! Absolutely never pool Virtual Threads!**
Pools exist because OS threads are expensive to create (1MB stack + OS syscalls).
Virtual threads cost almost nothing to create. Pooling them wastes memory and re-introduces thread contamination bugs.
- **Old way:** Create a pool of 200 threads and submit tasks.
- **Loom way:** Create a fresh Virtual Thread per task and discard it immediately:
```java
// Modern Java 21 idiomatic execution:
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> fetchUserDetails());
    executor.submit(() -> fetchOrderHistory());
} // Auto-closes and joins all tasks!
```

---

## 🔴 Tier 3: Virtual Threads vs. Reactive Netty (WebFlux)

A frequent question from staff engineers: *"If Java 21 has Virtual Threads, is Reactive Programming (Spring WebFlux / Netty) dead?"*

| Dimension | Virtual Threads (Spring MVC + Loom) | Reactive (Spring WebFlux + Netty) |
| :--- | :--- | :--- |
| **Programming Model** | **Imperative:** Standard `try-catch`, straight-line synchronous code, easy step-by-step debugging. | **Functional:** Monads (`Mono<T>`, `Flux<T>`), complex operator chains (`flatMap`, `zip`), difficult stack traces. |
| **Database Ecosystem** | Works out-of-the-box with standard **JDBC, JPA, and Hibernate**. | Requires completely separate non-blocking drivers (**R2DBC**), which lack full JPA maturity. |
| **Backpressure** | Relies on OS TCP socket buffers and thread pool queues. | **Native application-level backpressure** via Reactive Streams specification. |
| **Streaming / WebSockets** | Can do it, but requires long-lived virtual threads. | **Dominant:** Handles hundreds of thousands of long-lived, bi-directional event streams with near-zero overhead. |
| **Best Used For** | **Downstream business services:** CRUD, Database queries, Payment processing, standard REST APIs. | **Edge gateways:** High-density routing, WebSocket servers, SSE feeds (e.g. `api-gateway`). |

---

## 💎 Bonus: Modern Java 17/21 Language Upgrades

### 1. Java Records (Immutable Data Carriers)
Eliminates boilerplate for DTOs and value objects without Lombok:
```java
// A complete, immutable DTO with equals(), hashCode(), and toString()!
public record ProductDto(
    UUID id,
    String title,
    BigDecimal price,
    int stockQuantity
) {
    // Compact constructor for validation:
    public ProductDto {
        Objects.requireNonNull(title, "Title cannot be null");
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
    }
}
```

### 2. Pattern Matching for `switch`
```java
public String formatPaymentEvent(PaymentEvent event) {
    return switch (event) {
        case CardPayment cp -> "Card ending in " + cp.lastFour();
        case CryptoPayment crypto -> "Crypto wallet: " + crypto.walletAddress();
        case null -> "Unknown payment";
    };
}
```

---

## 🏆 Tier 4: Top 5 Tricky Tier-1 Interview Questions & Answers

### Q1: How does Project Loom unmount a Virtual Thread at the bytecode level during a blocking socket read?
**High-Scoring Answer:**
> "Under the hood, Project Loom is powered by **Continuations** (`jdk.internal.vm.Continuation`).
> 
> 1. When a virtual thread executes a blocking operation (like `SocketChannel.read()` or `Thread.sleep()`), the JDK internal networking implementation detects that the current thread is a `VirtualThread`.
> 2. Instead of issuing an OS blocking syscall, it registers the socket descriptor with the JVM's internal `Poller` (which uses Linux `epoll` or macOS `kqueue`).
> 3. It then calls `Continuation.yield()`. The JVM takes the virtual thread's execution call stack frames off the CPU register/stack and copies them into heap memory.
> 4. The Carrier OS thread is now released to run other work.
> 5. When the OS kernel signals that data has arrived on the socket via `epoll`, the Poller unparks the virtual thread, and schedules it onto the `ForkJoinPool` carrier queue to resume where it left off."

---

### Q2: Can Virtual Threads improve the performance of CPU-bound tasks (e.g., video transcoding or AES-256 encryption)?
**High-Scoring Answer:**
> "No. Virtual Threads provide zero performance improvement for CPU-bound workloads.
> Virtual Threads solve the problem of **threads waiting on I/O** (network, disk, DB locks). If a task requires 100% CPU computation, it must occupy a physical CPU core regardless of whether it is wrapped in a virtual thread or a platform thread. Running 100,000 CPU-intensive virtual threads on an 8-core machine will simply introduce thread scheduling overhead without any throughput gain."

---

### Q3: How do you detect if your application has Thread Pinning in production?
**High-Scoring Answer:**
> "You can launch the JVM with the diagnostic system property:
> `-Djdk.tracePinnedThreads=full` (or `=short`).
> 
> When a virtual thread attempts to park while pinned to its carrier thread, the JVM prints a complete stack trace to `System.err`, pinpointing the exact `synchronized` method or native JNI call responsible for the pinning."

---

### Q4: What is the difference between Java `record` and Lombok `@Data` or `@Value`?
**High-Scoring Answer:**
> "A Java `record` is a first-class citizen in the JVM specification. The compiler enforces that:
> 1. All fields are `private final`.
> 2. The class itself is `final` (cannot be extended).
> 3. Canonical constructors, accessors, `equals()`, `hashCode()`, and `toString()` are generated according to strict semantic rules.
> 4. Records support built-in Java serialization and **Record Patterns** in switch expressions (`case UserDto(var id, var email)`).
> Lombok `@Data`, by contrast, is an AST bytecode manipulation tool that generates mutable JavaBeans with setters, which can introduce accidental mutation bugs in concurrent code."

---

### Q5: How do you configure Spring Boot 3.2+ to run all HTTP controllers on Virtual Threads?
**High-Scoring Answer:**
> "In Spring Boot 3.2+, running on Java 21, you simply add one line to `application.yml`:
> ```yaml
> spring:
>   threads:
>     virtual:
>       enabled: true
> ```
> Spring Boot automatically configures the embedded Tomcat web server to use `Executors.newVirtualThreadPerTaskExecutor()` for handling all incoming HTTP requests, and sets all `@Async` task executors to use virtual threads."
