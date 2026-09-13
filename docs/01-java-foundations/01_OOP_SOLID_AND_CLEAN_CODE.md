# Deep Dive 01: OOP, SOLID, and Clean Code for Java Backend Development

> **Module:** `01-java-foundations`
>
> **Target audience:** Beginner Java developer to backend interview candidate
>
> **Prerequisites:** Classes, methods, interfaces, and constructors
>
> **Status:** Implemented in parts throughout this repository
>
> **Related code:** `user-service/src/main/java/com/ecommerce/user/service/AuthService.java`, `user-service/src/main/java/com/ecommerce/user/service/impl/AuthServiceImpl.java`
>
> **Last verified against:** Java 21 / Spring Boot 3.3.2

---

## 🟢 Tier 1: The Intuitive Mental Model

### OOP: Give each thing one clear responsibility

Think of a restaurant. The waiter takes orders, the chef prepares food, and the cashier handles payment. A restaurant becomes difficult to run if one person must do every job.

Java backend code follows the same idea:

- A controller translates HTTP requests into method calls.
- A service owns business decisions.
- A repository reads and writes data.
- A DTO carries data across a boundary.

These are not security boundaries by themselves. They are separation-of-concern boundaries that make code easier to read, test, and change.

### The four OOP tools

| Tool | Meaning | Backend example |
|---|---|---|
| Encapsulation | Keep state and its rules together | A `User` owns its password hash rather than exposing raw password mutation everywhere. |
| Abstraction | Expose what a caller needs, hide how it happens | `AuthService` exposes `login()` without exposing JWT or repository details. |
| Inheritance | Reuse a stable parent contract carefully | Framework classes such as `OncePerRequestFilter` are extended. |
| Polymorphism | Program against a contract with different implementations | Tests inject a mock implementation of an interface. |

### SOLID in one sentence each

- **S — Single Responsibility:** one reason for a class to change.
- **O — Open/Closed:** add behavior through extension/configuration without repeatedly rewriting stable code.
- **L — Liskov Substitution:** an implementation must honor its parent/interface contract.
- **I — Interface Segregation:** small focused interfaces are easier to implement correctly.
- **D — Dependency Inversion:** depend on abstractions, not concrete infrastructure.

---

## 🟡 Tier 2: Common Doubts and Anti-Patterns

### “Does one class always mean one responsibility?”

No. “One responsibility” means one coherent reason to change. `AuthServiceImpl` may coordinate authentication, token creation, and refresh-token persistence because they form one authentication use case. It should not also format HTTP responses or configure database connections.

### “Should every class have an interface?”

No. Add an interface when it represents a meaningful boundary: multiple implementations, a stable module contract, or easy substitution in tests. Creating `UserMapper` and `UserMapperImpl` with one implementation and no boundary is often ceremony, not design.

### “Is inheritance better than composition?”

Usually prefer composition. Inheritance tightly couples a child to a parent's implementation. Use it when a true framework or domain “is-a” relationship exists; use fields and interfaces when one object merely uses another.

### Anti-pattern: the God Service

```java
class UserService {
    void login() { }
    void sendEmail() { }
    void calculateOrderTotal() { }
    void uploadProductImage() { }
    void generateInvoicePdf() { }
}
```

This class has unrelated reasons to change. It becomes hard to test and creates merge conflicts. Split it by business capability, not by arbitrary file size.

---

## 🔴 Tier 3: Mechanics in Spring Applications

### Dependency inversion in the user service

```text
AuthController  →  AuthService  →  UserRepository / RefreshTokenRepository
                         ↓
                  JwtUtils / PasswordEncoder
```

`AuthController` depends on the `AuthService` contract rather than constructing `AuthServiceImpl`. Spring injects the implementation at startup. This makes the controller independent of token or database implementation details.

### Constructor injection

```java
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
}
```

`final` dependencies make required collaborators visible and immutable after construction. A test can create the service with mocks without starting Spring.

### Clean code rules that matter in interviews

- Prefer domain names: `refreshAccessToken`, not `doRefresh`.
- Keep controller code thin; place decisions in services.
- Return DTOs, not JPA entities, from API controllers.
- Put transaction boundaries around a business operation, not every repository call.
- Avoid boolean method parameters such as `createUser(user, true)`: use named methods or an enum instead.
- Comments should explain a non-obvious decision, not repeat the Java syntax.

---

## 🏆 Tier 4: Interview Questions and Answers

### Q1: What is dependency inversion in Spring?

High-level business code depends on an abstraction such as `AuthService` or `PaymentGateway`, while Spring supplies a concrete implementation. This prevents controllers from being coupled to construction details and allows replacement during testing or infrastructure changes.

### Q2: Why prefer constructor injection over field injection?

Constructor injection makes dependencies explicit, supports immutability, fails early when a dependency is missing, and makes unit tests straightforward. Field injection hides the required collaborators and needs reflection or a Spring context in many tests.

### Q3: When should you use inheritance?

Use inheritance for a stable “is-a” relationship or a framework extension point, such as extending `OncePerRequestFilter`. Prefer composition for application behavior because it limits coupling and allows collaborators to be changed independently.

### Q4: What does the Single Responsibility Principle actually mean?

It means a module should have one cohesive reason to change. It does not mean every method needs its own class. A class can contain several related methods as long as they serve the same business responsibility.

### Q5: Is programming to interfaces always required?

No. Use an interface at a meaningful boundary. A single stable implementation may be clearer as a concrete class; adding an interface just because a class exists creates unnecessary indirection.

---

## 🛠️ Tier 5: Apply It in This Repository

### Where it appears

- `AuthController` delegates authentication use cases to the `AuthService` contract.
- `AuthServiceImpl` composes repositories, token utilities, and a password encoder.
- `UserResponseDto` separates API output from the JPA `User` entity.

### Mini exercise

Add a `PasswordResetNotifier` interface with a development implementation that logs a message. Make `AuthServiceImpl` depend on the interface rather than directly deciding how notification delivery works.

### Failure scenario

- **Symptom:** A controller test requires a database, JWT keys, and a full Spring context.
- **Cause:** The controller constructs concrete services or contains business logic itself.
- **Fix:** Inject a narrow service contract and move the business decision into the service, then mock that contract in the controller test.

### Key takeaway

- Organize code around business responsibility.
- Prefer composition and constructor injection.
- Interfaces describe meaningful boundaries, not every class.
- Keep HTTP, business, and persistence concerns separate.
