# Module 1: Java Foundations

> **Status:** 🟢 Complete (All 6 Deep-Dives Live)
>
> **Prerequisites:** Basic Java syntax and the ability to compile and run a Java program.
>
> **Outcome:** Understand the language and JVM concepts that Spring, Hibernate, Kafka clients, and concurrent backend code rely on.

Read these lessons in order:

1. [OOP, SOLID, and Clean Code](01_OOP_SOLID_AND_CLEAN_CODE.md)
   - Encapsulation, Abstraction, Inheritance, Polymorphism in backend architectures.
   - S.O.L.I.D. principles applied to microservices without over-engineering.
2. [Collections, HashMap Internals, and equals()/hashCode() Contract](02_COLLECTIONS_HASHMAP_INTERNALS_EQUALS_HASHCODE.md)
   - Power-of-2 bitwise bucket index math, hash collisions, and load factors.
   - Singly linked list to Red-Black tree conversion (`TREEIFY_THRESHOLD = 8`).
   - Why `ConcurrentHashMap` avoids global locks using bucket CAS and node monitors.
3. [Exception Handling, Checked vs. Unchecked, and Optional Anti-Patterns](03_EXCEPTION_HANDLING_UNCHECKED_VS_CHECKED_AND_OPTIONAL.md)
   - The fatal Spring `@Transactional` silent commit bug on checked exceptions.
   - JVM native `fillInStackTrace()` performance overhead.
   - Avoiding `Optional.get()`, entity field misuse, and designing REST API exception hierarchies.
4. [Java Concurrency, Volatile, Synchronization, and Thread Pools](04_CONCURRENCY_THREADS_VOLATILE_AND_SYNCHRONIZATION.md)
   - Java Memory Model (JMM), `happens-before`, and CPU memory barriers.
   - Why `volatile` does NOT guarantee atomicity; Hardware CAS in `AtomicInteger`.
   - Production OOM dangers of `Executors.newFixedThreadPool()` unbounded queues.
   - `ThreadLocal` memory leaks in web server worker thread pools.
5. [JVM Memory Model, Garbage Collection Algorithms, and OOM Diagnostics](05_JVM_MEMORY_MODEL_GC_ALGORITHMS_AND_OOM_ANALYSIS.md)
   - Heap (Eden, Survivor, Tenured), Metaspace, Stack, and Off-Heap direct memory.
   - Weak Generational Hypothesis; G1 Region-based GC vs ZGC sub-millisecond pauses.
   - Diagnosing OOMs via Eclipse MAT heap dumps, and Kubernetes container cgroup limits.
6. [Modern Java: Virtual Threads (Project Loom) vs. Reactive Netty, Records & Patterns](06_MODERN_JAVA_VIRTUAL_THREADS_PROJECT_LOOM_VS_REACTIVE.md)
   - Platform threads vs. 1,000,000 Virtual Threads on `ForkJoinPool` carrier threads.
   - Continuation unmounting during blocking I/O and the deadly Thread Pinning bug.
   - When to use Spring MVC + Loom vs. Spring WebFlux + Netty.
   - Java Records, Pattern matching for switch, and Sealed classes.
