# Deep Dive 04: Java Concurrency, Volatile, Synchronization, and Thread Pools

> **Module:** `01-java-foundations`  
> **Target Audience:** Beginner Interns to Staff Backend Engineers / Tier-1 Interview Candidates  
> **Prerequisites:** Java Threads, Heap vs Stack memory  
> **Related Code in Project:** Rate limiting, Redis token bucket, Gateway filters, Async executors  
> **Last Verified Against:** Java 21 LTS  

---

## 🗺️ Visual Reading Order & Navigation
```text
[03_EXCEPTION_HANDLING_UNCHECKED_VS_CHECKED_AND_OPTIONAL.md]
              │
              ▼
[04_CONCURRENCY_THREADS_VOLATILE_AND_SYNCHRONIZATION.md]  ◄── YOU ARE HERE
              │
              ▼
[05_JVM_MEMORY_MODEL_GC_ALGORITHMS_AND_OOM_ANALYSIS.md]
```

---

## 🟢 Tier 1: The Intuitive Mental Model

### The Shared Kitchen Whiteboard Analogy
Imagine a restaurant kitchen with 4 cooks (Threads). In the center of the kitchen is a shared whiteboard listing the remaining inventory of Wagyu steaks (Shared Mutable State in RAM): `Steaks = 10`.

Each cook also has their own **notepad in their pocket** (L1/L2 CPU Cache):
1. **The Race Condition:**
   - Cook A and Cook B both get an order for 1 steak at the exact same moment.
   - Cook A reads `10` from the board, writes `10` on their notepad.
   - Cook B reads `10` from the board, writes `10` on their notepad.
   - Cook A subtracts 1 on their notepad (`9`) and writes `9` on the whiteboard.
   - Cook B subtracts 1 on their notepad (`9`) and writes `9` on the whiteboard.
   - **Result:** Two steaks were cooked, but the board says 9 instead of 8! One steak vanished from inventory!

2. **The Visibility Problem (`volatile`):**
   - If Cook A updates the board, but Cook B only ever looks at their personal pocket notepad, Cook B will never see Cook A's update. The keyword `volatile` forces all cooks to bypass their personal notepads and read/write directly to the main whiteboard every single time.

3. **The Mutex Lock (`synchronized`):**
   - Before touching the whiteboard, a cook must physically grab the **kitchen marker pen** (the Monitor Lock). Only one cook can hold the marker at a time. Other cooks must wait in line.

---

## 🟡 Tier 2: Common Doubts, Gotchas & Anti-Patterns

### 1. "Does `volatile` make operations thread-safe?"
**The Deadly Trap:**
```java
public class Counter {
    private volatile int count = 0;

    public void increment() {
        count++; // DANGER: NOT THREAD-SAFE!
    }
}
```
**Why this fails under concurrency:**
`count++` is **not a single atomic CPU instruction**. In bytecode, it consists of **three distinct steps**:
1. `GETFIELD`: Read `count` from memory into CPU register.
2. `IADD`: Increment the register value by 1.
3. `PUTFIELD`: Write the updated register value back to memory.

If Thread 1 reads `count = 5`, and Thread 2 reads `count = 5` before Thread 1 writes back, both will write `6`. One increment is lost!
- `volatile` guarantees **Visibility** (changes are immediately flushed to main memory).
- `volatile` does **NOT guarantee Atomicity** for compound operations!

> [!TIP]
> To achieve atomic increments without heavyweight locking, use `AtomicInteger`:
> ```java
> private final AtomicInteger count = new AtomicInteger(0);
> count.incrementAndGet(); // Uses hardware CPU CAS (Compare-And-Swap)
> ```

---

### 2. The Production ThreadPool Anti-Pattern: `Executors.newFixedThreadPool()`
Many junior developers write:
```java
ExecutorService executor = Executors.newFixedThreadPool(100);
```
**Why this causes `OutOfMemoryError` in Production:**
Let's inspect the JDK source code for `newFixedThreadPool`:
```java
public static ExecutorService newFixedThreadPool(int nThreads) {
    return new ThreadPoolExecutor(nThreads, nThreads,
                                  0L, TimeUnit.MILLISECONDS,
                                  new LinkedBlockingQueue<Runnable>()); // UNBOUNDED QUEUE!
}
```
Notice `new LinkedBlockingQueue<Runnable>()` without a capacity limit. Its default capacity is `Integer.MAX_VALUE` (2.14 billion tasks)!
- If downstream payment service or database slows down, tasks start queuing up.
- The queue consumes all available JVM heap.
- The application crashes with `java.lang.OutOfMemoryError: Java heap space`.

> [!CAUTION]
> **Production Rule:** Never use `Executors` convenience factory methods in production! Always instantiate `ThreadPoolExecutor` directly with a **bounded queue** and a clear **rejection policy**:
> ```java
> ThreadPoolExecutor executor = new ThreadPoolExecutor(
>     10,                              // Core pool size
>     50,                              // Max pool size
>     60L, TimeUnit.SECONDS,           // Keep-alive time for idle threads
>     new ArrayBlockingQueue<>(1000),  // BOUNDED QUEUE (max 1,000 tasks)
>     new CustomThreadFactory("api-worker"),
>     new ThreadPoolExecutor.CallerRunsPolicy() // Graceful backpressure!
> );
> ```

---

### 3. The `ThreadLocal` Memory Leak in Tomcat / Netty
Web servers use worker thread pools. When a request arrives, Thread #5 is picked from the pool.
If you store data in a `ThreadLocal`:
```java
public class UserContextHolder {
    private static final ThreadLocal<User> currentUser = new ThreadLocal<>();
    public static void set(User user) { currentUser.set(user); }
    public static User get() { return currentUser.get(); }
}
```
**The Bug:**
When the HTTP request finishes, Thread #5 is returned to the pool, but `currentUser` still points to the `User` object!
1. The `User` object cannot be garbage collected.
2. Worse: The next customer whose request is assigned to Thread #5 will inherit the previous user's credentials and see another customer's private data!

> [!IMPORTANT]
> Always clean up `ThreadLocal` in a `finally` block:
> ```java
> try {
>     UserContextHolder.set(authenticatedUser);
>     filterChain.doFilter(request, response);
> } finally {
>     UserContextHolder.clear(); // ThreadLocal.remove()
> }
> ```

---

## 🔴 Tier 3: Low-Level Internal Mechanics

### 1. The Java Memory Model (JMM) & `happens-before`
Modern multi-core CPUs have multi-level hardware caches:
```text
[ Core 1 ] ──► [ L1 Cache ] ──┐
                              ├──► [ Shared L3 Cache ] ──► [ Main Memory (RAM) ]
[ Core 2 ] ──► [ L1 Cache ] ──┘
```
To optimize performance, CPUs and Java JIT compilers reorder instructions (instruction pipelining).

The **`happens-before` relationship** is the JMM's formal guarantee that memory writes by Thread A are guaranteed to be visible to Thread B:
1. **Monitor Lock Rule:** An `unlock` on a monitor happens-before every subsequent `lock` on that same monitor.
2. **Volatile Variable Rule:** A write to a `volatile` field happens-before every subsequent read of that same field.
3. **Thread Start Rule:** Calling `thread.start()` happens-before any action in the started thread.

### 2. Hardware Memory Barriers (Fences)
Under the hood, when the JIT compiler sees a `volatile` write, it inserts a CPU hardware instruction called a **Memory Barrier** (e.g. `mfence` on x86, or `dmb` on ARM):
- Prevents reordering of writes before the barrier with reads/writes after the barrier.
- Forces the CPU store-buffer to flush immediately to cache/RAM, making the value visible across all CPU cores.

---

## 🏆 Tier 4: Top 5 Tricky Tier-1 Interview Questions & Answers

### Q1: What is the difference between `synchronized` and `ReentrantLock`?
| Feature | `synchronized` | `ReentrantLock` |
| :--- | :--- | :--- |
| **Type** | Java language keyword | JDK class in `java.util.concurrent.locks` |
| **Acquisition** | Block-structured (implicit acquire/release) | Explicit (`lock.lock()` and `lock.unlock()` in `finally`) |
| **Fairness** | Unfair only | Can be Fair or Unfair (`new ReentrantLock(true)`) |
| **Interruptible** | No (thread blocks indefinitely until lock free) | Yes (`lock.lockInterruptibly()`) |
| **Timed Try** | No | Yes (`lock.tryLock(5, TimeUnit.SECONDS)`) |
| **Multiple Conditions** | Single wait-set (`wait()`, `notify()`) | Multiple condition variables (`newCondition()`) |

---

### Q2: How does Double-Checked Locking work for Singleton, and why was `volatile` strictly required?
**The Classic Code:**
```java
public class Singleton {
    private static volatile Singleton instance; // MUST BE VOLATILE!

    public static Singleton getInstance() {
        if (instance == null) {                         // 1st Check (No lock)
            synchronized (Singleton.class) {
                if (instance == null) {                 // 2nd Check (With lock)
                    instance = new Singleton();
                }
            }
        }
        return instance;
    }
}
```
**High-Scoring Answer:**
> "The expression `instance = new Singleton()` is executed in 3 steps by the JVM:
> 1. Allocate memory on heap.
> 2. Invoke constructor to initialize fields.
> 3. Assign the memory address to the reference `instance`.
> 
> Without `volatile`, the CPU/compiler can reorder steps 2 and 3 (1 -> 3 -> 2). If Thread A finishes step 3 before step 2, Thread B enters the 1st check, sees `instance != null`, and returns an **uninitialized half-constructed object**, causing immediate `NullPointerException` or corrupted state. `volatile` establishes a memory barrier preventing instruction reordering."

---

### Q3: How does Hardware Compare-And-Swap (CAS) work in `AtomicInteger`?
**High-Scoring Answer:**
> "CAS is an atomic CPU instruction (such as `cmpxchg` on x86). It accepts three operands:
> - Memory location $V$
> - Expected old value $A$
> - New value $B$
> 
> The CPU atomically checks: *'If the value at $V$ is still equal to $A$, update it to $B$; otherwise do nothing and report failure.'*
> 
> In `AtomicInteger.incrementAndGet()`, this runs in an optimistic spin-lock loop:
> ```java
> do {
>     oldValue = get();
>     newValue = oldValue + 1;
> } while (!compareAndSet(oldValue, newValue));
> ```
> Since no OS thread context switching occurs, CAS is significantly faster than `synchronized` locks when thread contention is moderate."

---

### Q4: What happens when all threads in a `ThreadPoolExecutor` are busy and the queue is full?
**High-Scoring Answer:**
> "The executor delegates to its configured `RejectedExecutionHandler`. Java provides 4 built-in policies:
> 1. **`AbortPolicy` (Default):** Throws `RejectedExecutionException`.
> 2. **`CallerRunsPolicy` (Best for Backpressure):** The calling thread (e.g. the HTTP worker thread) executes the task itself. This naturally slows down incoming submissions, acting as an automatic throttle.
> 3. **`DiscardPolicy`:** Silently drops the task with zero error.
> 4. **`DiscardOldestPolicy`:** Drops the oldest unhandled task at the head of the queue, then retries execution."

---

### Q5: What is Deadlock, and what are the 4 Coffman Conditions required for a deadlock to occur?
**High-Scoring Answer:**
> "A deadlock occurs when two or more threads are permanently blocked, each waiting for a lock held by the other (e.g. Thread 1 holds Lock A, wants Lock B; Thread 2 holds Lock B, wants Lock A).
> 
> For a deadlock to occur, **all four Coffman conditions** must hold simultaneously:
> 1. **Mutual Exclusion:** Resources cannot be shared; only one thread can hold a resource at a time.
> 2. **Hold and Wait:** A thread holding a resource can request additional resources without releasing what it holds.
> 3. **No Preemption:** Resources cannot be forcibly taken from a thread; only the holding thread can release it.
> 4. **Circular Wait:** A closed chain of threads exists where each thread waits for a resource held by the next.
> 
> **Prevention:** We eliminate condition #4 by enforcing a **strict global lock acquisition order** (e.g. always acquire Lock A before Lock B everywhere in the codebase)."
