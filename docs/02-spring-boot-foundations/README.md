# Module 2: Spring Boot Foundations

> **Status:** 🟢 Complete (All 4 Deep-Dives Live)
>
> **Prerequisites:** Complete Module 1, especially OOP, exceptions, and concurrency.
>
> **Outcome:** Explain how Spring creates objects, handles HTTP requests, validates input, and applies transactions.

Read these lessons in order:

1. [The Spring IoC Container, Dependency Injection, and Bean Lifecycle](01_IOC_CONTAINER_DEPENDENCY_INJECTION_AND_BEAN_LIFECYCLE.md)
   - `BeanFactory` vs `ApplicationContext`; Why constructor injection is the gold standard.
   - Complete 8-stage Bean lifecycle and where AOP dynamic proxies are born.
   - Resolving circular dependencies and why Spring Boot 2.6+ banned them.
2. [Spring Boot Auto-Configuration, Conditionals, and Custom Starters](02_SPRING_BOOT_AUTO_CONFIGURATION_AND_CONDITIONALS.md)
   - How `@SpringBootApplication` works without magic.
   - `@ConditionalOnMissingBean` and `@ConditionalOnClass` mechanics.
   - Spring Boot 2.x `spring.factories` vs Spring Boot 3.x `AutoConfiguration.imports`.
3. [The Spring MVC Request Lifecycle: Filters, Interceptors, DispatcherServlet, and AOP](03_SPRING_MVC_REQUEST_LIFECYCLE_FILTERS_INTERCEPTORS_AOP.md)
   - The exact journey of an HTTP request from TCP socket to Controller.
   - Servlet Filters vs HandlerInterceptors vs Spring AOP Aspect decision matrix.
   - Why reading the HTTP request body twice throws `HttpMessageNotReadableException`.
4. [Spring Transaction Management, Dynamic Proxies, and the Self-Invocation Pitfall](04_SPRING_TRANSACTION_MANAGEMENT_PROXIES_AND_SELF_INVOCATION.md)
   - How `@Transactional` wraps methods in CGLIB subclass proxies.
   - The infamous self-invocation bug where `this.method()` bypasses transaction boundaries.
   - Transaction propagation (`REQUIRED` vs `REQUIRES_NEW`) and rollback rules.
