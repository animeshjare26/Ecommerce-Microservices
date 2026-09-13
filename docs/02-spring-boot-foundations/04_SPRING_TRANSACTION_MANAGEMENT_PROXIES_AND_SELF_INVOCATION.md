# Deep Dive 04: Spring Transaction Management, Dynamic Proxies, and the Self-Invocation Pitfall

> **Module:** `02-spring-boot-foundations`  
> **Target Audience:** Beginner Interns to Principal Architects / Tier-1 Interview Candidates  
> **Prerequisites:** Spring IoC & Proxies (Deep Dive 01), Exception Handling (Module 01)  
> **Related Code in Project:** `@Transactional` in `user-service` (`AuthServiceImpl`), Upcoming `product-service` stock updates  
> **Last Verified Against:** Spring Boot 3.3.2 / Spring Framework 6.1  

---

## 🗺️ Visual Reading Order & Navigation
```text
[03_SPRING_MVC_REQUEST_LIFECYCLE_FILTERS_INTERCEPTORS_AOP.md]
                                   │
                                   ▼
[04_SPRING_TRANSACTION_MANAGEMENT_PROXIES_AND_SELF_INVOCATION.md]  ◄── YOU ARE HERE
                                   │
                                   ▼
[03-database-and-persistence/README.md]
```

---

## 🟢 Tier 1: The Intuitive Mental Model

### The Real Estate Escrow Agent Analogy
Imagine buying a house for $500,000:
1. You hand $500,000 to the seller.
2. The seller hands you the deed of ownership.

If step 1 succeeds, but right before step 2 the seller has a heart attack, the seller's heirs might claim the money while keeping the house.
To prevent this, you hire a **Licensed Escrow Agent** (the `@Transactional` Proxy):
- The Escrow Agent freezes the money and title in an escrow vault (**`connection.setAutoCommit(false)`**).
- Only when both signatures are fully executed does the Escrow Agent release the funds and deed simultaneously (**`connection.commit()`**).
- If anything fails at any microsecond during the process, the Escrow Agent returns all money to the buyer and all deeds to the seller (**`connection.rollback()`**).

```text
Caller ──► [ CGLIB Transactional Proxy ] ──► [ Real UserService Instance ]
                    │                                     │
         1. Open Connection & setAutoCommit(false)        │
         2. Bind Connection to ThreadLocal                │
         3. Delegate ─────────────────────────────────────► Execute SQL (INSERT, UPDATE)
         4. Commit / Rollback on Exception ◄────────────── Returns Result / Throws
```

---

## 🟡 Tier 2: The #1 Spring Interview Pitfall: The Self-Invocation Bug

### The Disaster Scenario
A junior developer writes this code to register a user and grant bonus credits:

```java
@Service
public class UserService {

    public void registerUser(UserRequest req) {
        // Step 1: Save basic profile
        userRepository.save(new User(req.getEmail()));

        // Step 2: Call internal transactional method to add welcome credits
        this.grantBonusCredits(req.getEmail()); // ◄── THE TRAP!
    }

    @Transactional // DANGER: THIS ANNOTATION IS COMPLETELY IGNORED!
    public void grantBonusCredits(String email) {
        creditRepository.addCredits(email, 100);
        if (true) {
            throw new RuntimeException("Simulated payment gateway crash!");
        }
    }
}
```

**What happens in production?**
1. An exception is thrown in `grantBonusCredits()`.
2. **THE TRANSACTION NEVER ROLLS BACK! THE USER IS SAVED, AND THE CREDITS ARE COMMITTED!**
3. Why? Because `@Transactional` was **completely ignored by Spring**!

### The Bytecode Reason: Why It Bypasses the Proxy
When another class (e.g. `UserController`) injects `UserService`, Spring injects the **CGLIB Dynamic Proxy**, not the raw instance.
1. When `UserController` calls `registerUser()`, it calls the proxy.
2. But inside `registerUser()`, `this.grantBonusCredits()` is executed by the **raw target instance**.
3. The keyword `this` points to the unproxied instance in heap memory.
4. Because the call **never passes through the proxy**, the `TransactionInterceptor` never executes, no connection is configured with `setAutoCommit(false)`, and no transaction is opened!

```text
[ UserController ]
       │
       ▼
[ CGLIB Proxy ] ──────────► [ UserService (Target) ]
                            │  registerUser()
                            │    │
                            │    └── this.grantBonusCredits()  ◄── BYPASSES PROXY!
                            │        (NO TRANSACTION OPENED!)
```

### 3 Ways to Fix the Self-Invocation Bug

#### Solution 1: Extract to a Separate Service (Recommended / Clean Code)
Move `grantBonusCredits()` into a dedicated `UserCreditService`. When `UserService` calls `userCreditService.grantBonusCredits()`, the call goes through the Spring proxy cleanly.

#### Solution 2: Self-Injection with `@Lazy`
```java
@Service
public class UserService {
    @Lazy
    @Autowired
    private UserService self; // Injects the CGLIB proxy reference!

    public void registerUser(UserRequest req) {
        userRepository.save(new User(req.getEmail()));
        self.grantBonusCredits(req.getEmail()); // Calls through proxy!
    }
}
```

#### Solution 3: `AopContext.currentProxy()`
Requires `@EnableAspectJAutoProxy(exposeProxy = true)`:
```java
((UserService) AopContext.currentProxy()).grantBonusCredits(req.getEmail());
```

---

## 🔴 Tier 3: Low-Level Internal Mechanics: ThreadLocal & Connection Binding

How does Spring ensure that multiple DAOs/Repositories called inside one `@Transactional` method use the **exact same database connection**?

```text
                    [ TransactionSynchronizationManager ]
                                      │
               Stores Map<Object, Object> in ThreadLocal
                                      │
                Key: DataSource ──► Value: ConnectionHolder
                                      │
       ┌──────────────────────────────┴──────────────────────────────┐
       ▼                                                             ▼
[ UserRepository ]                                            [ OrderRepository ]
Calls DataSourceUtils.getConnection()                         Calls DataSourceUtils.getConnection()
──► Returns same Connection from ThreadLocal!                 ──► Returns same Connection from ThreadLocal!
```

1. When entering `@Transactional`, Spring's `JpaTransactionManager` or `DataSourceTransactionManager` pulls a physical connection from HikariCP.
2. It sets `connection.setAutoCommit(false)`.
3. It binds this connection to **`TransactionSynchronizationManager`** using a `ThreadLocal`.
4. When your Spring Data JPA repositories execute queries, they call `DataSourceUtils.getConnection()`, which retrieves the **bound connection from the current thread**.
5. When the method completes, `TransactionAspectSupport` issues `connection.commit()` and unbinds the connection from the `ThreadLocal`.

---

## 🏆 Tier 4: Top 5 Tricky Tier-1 Interview Questions & Answers

### Q1: What are Transaction Propagation levels, and what is the difference between `REQUIRED` and `REQUIRES_NEW`?
**The 7 Propagation Levels:**
- **`REQUIRED` (Default):** If an active transaction exists, join it. If not, create a new one.
- **`REQUIRES_NEW`:** Always create a brand-new transaction. If an active transaction exists, **suspend it** and open a separate physical database connection.

**The Tricky Production Question:**
> *"If Service A (`REQUIRED`) calls Service B (`REQUIRES_NEW`), and Service B throws an unchecked exception that is caught by Service A in a try-catch block, what happens?"*
> 
> **Staff-Level Answer:**
> "Service B's independent transaction will roll back. However, because Service A caught the exception in a `try-catch`, Service A's transaction can successfully commit! 
> 
> BUT: If Service B had been `REQUIRED` (joining Service A's transaction), Service B's failure would mark the shared transaction as **`rollback-only`**. When Service A attempts to commit, Spring will throw `UnexpectedRollbackException: Transaction marked as rollback-only`, rolling back both operations despite Service A's try-catch block!"

---

### Q2: Why does `@Transactional` have no effect when applied to a `private` method?
**High-Scoring Answer:**
> "Spring AOP uses CGLIB subclassing by default. CGLIB creates a subclass of your bean and overrides public methods to inject proxy logic.
> 
> Because `private` methods cannot be overridden by subclasses in Java, CGLIB proxies cannot intercept them. Spring's `TransactionAttributeSource` explicitly ignores non-public methods on CGLIB proxies, silently discarding the `@Transactional` annotation without throwing an error."

---

### Q3: What is the difference between JPA Dirty Checking and Spring `@Transactional(readOnly = true)`?
**High-Scoring Answer:**
> - **Default `@Transactional`:** Hibernate maintains an in-memory snapshot of every managed entity loaded from the DB. At transaction commit (`flush()`), it iterates over all entities, comparing current state against the initial snapshot (**Dirty Checking**) and generating `UPDATE` queries for any differences.
> - **`@Transactional(readOnly = true)`:**
>   1. **Hibernate Optimization:** Sets the Hibernate Flush Mode to `FlushMode.MANUAL`. Hibernate skips creating snapshot copies and skips the dirty checking pass, reducing CPU usage and memory footprint.
>   2. **Database Optimization:** On databases like PostgreSQL or MySQL, the driver or replica router can redirect the read query directly to a **Read-Only Database Replica**, saving master DB capacity."

---

### Q4: What happens if a method annotated with `@Transactional` takes 10 seconds to execute because of a slow external REST API call?
**High-Scoring Answer:**
> "This is a severe **connection pool exhaustion anti-pattern**.
> 
> A database connection from HikariCP is checked out at the **very start** of the `@Transactional` method and held open across the entire 10-second duration.
> If your HikariCP pool has 10 connections, and only 10 requests arrive simultaneously, all 10 connections become trapped waiting on the external HTTP call.
> No other user in the entire company can perform any database read or write, causing application-wide downtime!
> 
> **Best Practice:** Keep `@Transactional` tightly scoped *only* around fast database operations. Perform third-party HTTP calls outside the transaction boundary!"

---

### Q5: How does `@Transactional` behave with checked exceptions vs unchecked exceptions?
**High-Scoring Answer:**
> "By default, Spring transactions roll back **only** on unchecked exceptions (`RuntimeException` and `Error`). If a checked `Exception` (e.g. `IOException`, `SQLException`, or custom `PaymentException extends Exception`) is thrown, Spring **commits** the transaction!
> 
> To ensure rollback on all exceptions, always specify:
> `@Transactional(rollbackFor = Exception.class)`"
