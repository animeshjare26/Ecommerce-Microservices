# Deep Dive 03: The Spring MVC Request Lifecycle: Filters, Interceptors, DispatcherServlet, and AOP

> **Module:** `02-spring-boot-foundations`  
> **Target Audience:** Beginner Interns to Senior Architects / Tier-1 Interview Candidates  
> **Prerequisites:** Spring IoC Container, Bean Lifecycle (Deep Dive 01)  
> **Related Code in Project:** `CorrelationIdFilter`, `JwtAuthenticationFilter`, `GlobalExceptionHandler`, `AuthController`  
> **Last Verified Against:** Spring Boot 3.3.2 / Spring Framework 6.1  

---

## 🗺️ Visual Reading Order & Navigation
```text
[02_SPRING_BOOT_AUTO_CONFIGURATION_AND_CONDITIONALS.md]
                                   │
                                   ▼
[03_SPRING_MVC_REQUEST_LIFECYCLE_FILTERS_INTERCEPTORS_AOP.md]  ◄── YOU ARE HERE
                                   │
                                   ▼
[04_SPRING_TRANSACTION_MANAGEMENT_PROXIES_AND_SELF_INVOCATION.md]
```

---

## 🟢 Tier 1: The Intuitive Mental Model

### The International Airport Analogy
Imagine traveling on an international flight:
1. **The Airport Entrance & Border Control (Servlet Filters):**
   - Metal detectors, baggage X-rays, and passport checks.
   - They do not care which flight or gate you are taking. Their job is low-level security, rate limiting, and stamping a boarding pass with an ID. If you have no passport, you are turned away immediately. This is **Servlet Filter (`OncePerRequestFilter`)**.
2. **The Main Terminal Information Board (DispatcherServlet):**
   - The central hub where all passengers converge. It looks up your flight number and directs you to Gate 14. This is the **Front Controller (`DispatcherServlet`)**.
3. **The VIP Lounge & Gate Concierge (HandlerInterceptors):**
   - Knows which exact gate and flight you are boarding. Can check business-class loyalty status or log gate boarding times. This is **HandlerInterceptor (`preHandle`)**.
4. **The Aircraft Cabin (Controller):**
   - Where the actual journey happens. This is the **`@RestController`** executing business logic.
5. **The Flight Black Box Recorder (AOP Aspects):**
   - Silently records altitude, speed, and audio, wrapping around the pilot's actions without the pilot having to manually write logs. This is **Spring AOP (`@Around`)**.

```text
HTTP Request
     │
     ▼
[ Servlet Container (Tomcat) ]
     │
     ▼
[ Servlet Filters ] ── (Security, CORS, Correlation IDs)
     │
     ▼
[ DispatcherServlet ] ── (The Front Controller)
     │
     ▼
[ HandlerMapping ] ── (Finds matching @GetMapping)
     │
     ▼
[ HandlerInterceptor.preHandle() ]
     │
     ▼
[ HttpMessageConverter ] ── (Jackson JSON ──► Java DTO) + Bean Validation (@Valid)
     │
     ▼
[ AOP Aspect @Around ] ── (Metrics, Distributed Tracing)
     │
     ▼
[ @RestController Method ] ── (Business Logic)
     │
     ▼
[ HandlerInterceptor.postHandle() ]
     │
     ▼
[ HandlerInterceptor.afterCompletion() ] ── (Cleanup, Metrics)
     │
     ▼
HTTP Response (JSON)
```

---

## 🟡 Tier 2: Filters vs. Interceptors vs. AOP: When to Use Which?

Developers frequently confuse these three interception layers. Here is the definitive decision matrix:

| Feature | Servlet Filter | HandlerInterceptor | Spring AOP Aspect |
| :--- | :--- | :--- | :--- |
| **Layer / Scope** | **Servlet Container level** (Outside Spring Context) | **Spring MVC level** (Inside Spring Context) | **Any Spring Bean layer** (Service, Repo, Controller) |
| **Aware of Handler?** | ❌ No (Only sees raw `HttpServletRequest`) | ✅ Yes (Knows target Controller class & Method) | ✅ Yes (Knows exact method signature & args) |
| **Modifies Request/Response?** | ✅ Yes (`HttpServletRequestWrapper`) | ❌ Difficult (Cannot easily wrap response body) | ❌ No |
| **Aware of Exceptions?** | Only sees uncaught exceptions escaping MVC | `afterCompletion(Exception ex)` | `@AfterThrowing` |
| **Best Use Cases** | **Security headers, JWT parsing, CORS, GZIP decompression, Request ID injection.** | **Authentication session checks, locale changing, controller execution timers.** | **Business auditing, custom method caching, transaction management (`@Transactional`).** |

---

## 🔴 Tier 3: Low-Level Internal Mechanics: The `DispatcherServlet` Flow

When a byte buffer from Tomcat reaches `DispatcherServlet.doDispatch()`, Spring executes this exact 7-step sequence:

```java
protected void doDispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
    HandlerExecutionChain mappedHandler = null;
    try {
        // 1. Find the HandlerExecutionChain (Controller + Interceptors)
        mappedHandler = getHandler(request); // HandlerMapping
        
        // 2. Find the HandlerAdapter (e.g. RequestMappingHandlerAdapter)
        HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());

        // 3. Execute Interceptors: preHandle()
        if (!mappedHandler.applyPreHandle(request, response)) {
            return; // Interceptor aborted request (e.g. 401 Unauthorized)
        }

        // 4. Invoke the Controller method via reflection + argument resolvers
        ModelAndView mv = ha.handle(request, response, mappedHandler.getHandler());

        // 5. Execute Interceptors: postHandle()
        mappedHandler.applyPostHandle(request, response, mv);
    } catch (Exception ex) {
        // 6. Handle exception via HandlerExceptionResolver (@ExceptionHandler)
        processHandlerException(request, response, mappedHandler, ex);
    } finally {
        // 7. Execute Interceptors: afterCompletion() (Runs even on error!)
        mappedHandler.triggerAfterCompletion(request, response, null);
    }
}
```

---

## 🏆 Tier 4: Top 5 Tricky Tier-1 Interview Questions & Answers

### Q1: How does Spring Security integrate into the standard Servlet Filter chain?
**High-Scoring Answer:**
> "Servlet containers (like Tomcat) do not understand Spring beans natively. Spring bridges this gap using **`DelegatingFilterProxy`**:
> 1. In the servlet configuration, a single standard filter named `DelegatingFilterProxy` is registered.
> 2. When an HTTP request arrives, `DelegatingFilterProxy` looks up a Spring bean named `springSecurityFilterChain` inside the `WebApplicationContext`.
> 3. This bean is an instance of **`FilterChainProxy`**, which manages the ordered sequence of Spring Security filters (e.g., `CorsFilter`, `CsrfFilter`, `JwtAuthenticationFilter`, `AuthorizationFilter`)."

---

### Q2: What happens if an exception is thrown inside `HandlerInterceptor.preHandle()` vs inside a Controller method?
**High-Scoring Answer:**
> - **Inside a Controller Method:** The exception is caught by `DispatcherServlet` and routed to the registered `HandlerExceptionResolver` (which invokes methods annotated with `@ExceptionHandler` inside `@RestControllerAdvice`). Interceptors' `afterCompletion()` is still invoked with the exception object passed in.
> - **Inside `preHandle()`:** The execution chain immediately terminates. The Controller method is **never invoked**. However, `afterCompletion()` is called *only* on the previous interceptors that had already succeeded, allowing them to clean up any thread-local resources."

---

### Q3: Why can't you read the HTTP Request Body twice in a standard Servlet Filter?
**High-Scoring Answer:**
> "The HTTP request body is an `InputStream` (`request.getInputStream()`). In standard TCP stream socket operations, bytes can only be read sequentially once; the stream pointer advances to the end of stream (`EOF`) and cannot be reset.
> 
> If a Filter reads the stream (e.g., to compute an HMAC signature or log the JSON body), the downstream Controller's `@RequestBody` Jackson deserializer will encounter an empty stream and throw `HttpMessageNotReadableException: Required request body is missing`.
> 
> **Solution:** Wrap the request in a **`ContentCachingRequestWrapper`** or a custom `HttpServletRequestWrapper` that reads the stream once and stores the bytes in a cached `byte[]` array, allowing downstream components to read it repeatedly."

---

### Q4: What is the difference between `@ControllerAdvice` and `@RestControllerAdvice`?
**High-Scoring Answer:**
> "`@RestControllerAdvice` is a meta-annotation composed of:
> - `@ControllerAdvice`
> - `@ResponseBody`
> 
> When an `@ExceptionHandler` method inside a standard `@ControllerAdvice` returns an object, Spring MVC assumes it is a view template name (for Thymeleaf or JSP). With `@RestControllerAdvice`, the return value is automatically processed by `HttpMessageConverter` (Jackson) and written directly to the HTTP response body as JSON."

---

### Q5: How does Spring MVC automatically validate request bodies using `@Valid` or `@Validated`?
**High-Scoring Answer:**
> "When `RequestMappingHandlerAdapter` invokes a controller method, it uses **`RequestResponseBodyMethodProcessor`** (an `HandlerMethodArgumentResolver`):
> 1. Reads the HTTP request stream and invokes Jackson `ObjectMapper` to instantiate the DTO.
> 2. Detects the `@Valid` (Jakarta Validation) or `@Validated` (Spring variant) annotation on the parameter.
> 3. Invokes the registered `Validator` (Hibernate Validator engine) to inspect annotations (`@NotBlank`, `@Email`, `@Min`).
> 4. If any constraint fails, it populates a `BindingResult` / `MethodArgumentNotValidException`. If the controller method does not accept `BindingResult` as an argument, Spring throws `MethodArgumentNotValidException`, which triggers the `@ExceptionHandler` to return HTTP 400 Bad Request."
