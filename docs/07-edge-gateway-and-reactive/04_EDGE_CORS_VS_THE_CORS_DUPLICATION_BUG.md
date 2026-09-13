# Deep Dive 04: Edge CORS vs. The CORS Duplication Bug

> **Module:** `07-edge-gateway-and-reactive`  
> **Target Audience:** From Beginner Intern to Principal Architect  
> **Core Concept:** Cross-Origin Resource Sharing, Preflight OPTIONS, Header Duplication Bug, Centralized Edge Proxying.

> **Status:** Implemented
>
> **Related code:** `api-gateway/src/main/java/com/ecommerce/gateway/config/CorsConfig.java`
>
> **Last verified against:** Spring Boot 3.3.2 / Spring Cloud 2023.0.3

---

## 🟢 Tier 1: The Intuitive Mental Model (For Beginners & Interns)

### The Security Guard & Courier Analogy

Imagine two buildings on a city street:
- **Building A:** A web browser running `http://localhost:3000` (React frontend).
- **Building B:** An e-commerce server running `http://localhost:8080` (API Gateway).

By default, the browser enforces the **Same-Origin Policy (SOP)**:
- A script loaded from `localhost:3000` is **forbidden** from reading private customer data from `localhost:8080` unless Building B explicitly grants permission.

#### The HTTP OPTIONS Preflight Protocol:
Before the browser sends a sensitive request (e.g. `POST /api/v1/auth/login` with a JSON body):
1. The browser sends a courier (**HTTP OPTIONS request**):
   *"Hey Building B, my client is localhost:3000. Will you permit me to send a POST request with an Authorization header?"*
2. Building B checks its security policy:
   - If Building B replies: *"Yes! `Access-Control-Allow-Origin: http://localhost:3000` and `Access-Control-Allow-Methods: POST`"*,
   - The browser sends the actual login request!
   - If Building B refuses or ignores the OPTIONS request, the browser **blocks the JavaScript code immediately!**

```
Browser (localhost:3000)                                   API Gateway (localhost:8080)
      │                                                                  │
      │ ───(1) HTTP OPTIONS /api/v1/auth/login (Preflight)─────────────► │
      │ ◄──(2) 200 OK (Access-Control-Allow-Origin: localhost:3000)───── │
      │                                                                  │
      │ ───(3) HTTP POST /api/v1/auth/login (Actual Request)───────────► │
      │ ◄──(4) 200 OK (JWT Access Token)──────────────────────────────── │
```

---

## 🟡 Tier 2: Clearing Common Doubts & Anti-Patterns

### Doubt 1: "Why does Postman or curl work fine, but my React app gets a CORS error?"
**Because CORS is strictly a BROWSER security feature!**
- Tools like Postman, curl, Python scripts, and backend servers do NOT enforce the Same-Origin Policy.
- Only web browsers (Chrome, Firefox, Safari) enforce CORS to protect end-users from malicious websites reading session data from another tab!

### Doubt 2: "What is the notorious 'CORS Duplication Bug'?"
This is one of the most frustrating errors in microservices:
1. A developer configures CORS inside `api-gateway` (`Access-Control-Allow-Origin: *`).
2. Another developer also configures CORS inside `user-service` (`@CrossOrigin` or `SecurityFilterChain`).
3. When the request flows through the Gateway to `user-service`, **both services append the CORS header!**
4. The HTTP response arrives at the browser containing:
   ```http
   Access-Control-Allow-Origin: http://localhost:3000, http://localhost:3000
   ```
5. **The Browser Crash:** The W3C specification strictly states that `Access-Control-Allow-Origin` can contain **at most ONE origin**. When the browser sees duplicate comma-separated origins, it instantly rejects the request with a cryptic CORS failure!

---

## 🔴 Tier 3: Low-Level Internal Mechanics (For Senior Engineers)

### Why Centralized Edge CORS Solves the Problem Permanently

```
                                  [ Centralized Edge CORS ]
Client (localhost:3000) ──► [ Spring Cloud API Gateway ] ──► [ Microservices (Private Subnet) ]
                                  │
                                  ├── 1. Handles ALL preflight OPTIONS requests at Port 8080!
                                  ├── 2. Appends Access-Control-Allow-* headers ONCE.
                                  └── 3. Downstream services need ZERO CORS configuration!
```

1. **Preflight Interception at the Edge:**
   - In `CorsConfig.java`, Netty's `CorsWebFilter` intercepts `OPTIONS` requests before they ever reach the routing filters.
   - The Gateway immediately returns `200 OK` with CORS headers in $< 0.1\text{ms}$.
   - **Benefit:** Preflight requests **never touch downstream microservices**, saving backend network bandwidth and CPU cycles!
2. **Eliminating Backend Duplication:**
   - Downstream microservices (`user-service`, `product-service`, `order-service`) sit in an internal private network.
   - They do not need any `@CrossOrigin` annotations or Spring Security `cors()` beans!

---

## 🏆 Tier 4: Top 5 Tricky Tier-1 Interview Questions & Winning Answers

### Q1: What constitutes an "Origin" in browser security?
**Answer:**
An origin is defined strictly by the tuple of **(Scheme, Host, Port)**:
- `http://localhost:3000` and `http://localhost:8080` are **different origins** (different ports).
- `http://example.com` and `https://example.com` are **different origins** (different schemes: HTTP vs. HTTPS).
- `https://api.example.com` and `https://app.example.com` are **different origins** (different subdomains).

### Q2: What is a "Simple Request" vs. a "Preflighted Request"?
**Answer:**
A request bypasses the preflight `OPTIONS` check **only if** it satisfies all three conditions:
1. HTTP Method is `GET`, `POST`, or `HEAD`.
2. Headers contain **only** safe-listed headers (`Accept`, `Accept-Language`, `Content-Language`, `Content-Type`).
3. `Content-Type` is strictly `application/x-www-form-urlencoded`, `multipart/form-data`, or `text/plain`.
Since modern SPA clients send `Content-Type: application/json` or `Authorization: Bearer <jwt>`, **almost all microservice REST calls require preflight!**

### Q3: Why is `Access-Control-Allow-Origin: *` forbidden when `allowCredentials: true`?
**Answer:**
Because of the **Credential Exfiltration Attack**:
If a malicious website (`evil-hacker.com`) could send requests with `credentials: true` (attaching your banking cookies) to an API that allows `*` (wildcard origin), the hacker's JavaScript could read your private banking balance!
- The W3C specification strictly mandates: If `Access-Control-Allow-Credentials: true` is set, the server **MUST return an explicit, non-wildcard origin** (`Access-Control-Allow-Origin: http://localhost:3000`).

### Q4: What does `Access-Control-Max-Age` do, and what is its performance impact?
**Answer:**
`Access-Control-Max-Age` (e.g. `3600` seconds in our `CorsConfig.java`) tells the browser to **cache the preflight result for 1 hour**.
- Without this header, the browser sends an `OPTIONS` request **before every single API call**, doubling the number of HTTP requests your infrastructure must handle!
- Setting a 1-hour cache eliminates 50% of incoming edge HTTP traffic for active users!

### Q5: What does `Access-Control-Expose-Headers` do?
**Answer:**
By default, the browser's `fetch()` or `Axios` API allows JavaScript to read only 7 basic response headers (`Cache-Control`, `Content-Language`, `Content-Length`, `Content-Type`, `Expires`, `Last-Modified`, `Pragma`).
If your backend sends custom metadata—such as **`X-Correlation-Id`** for distributed tracing or **`X-RateLimit-Remaining`**—JavaScript CANNOT read them unless the Gateway explicitly includes:
```http
Access-Control-Expose-Headers: X-Correlation-Id, X-RateLimit-Remaining
```

---

## 🛠️ Tier 5: Apply It in This Repository

### Where it appears
- CORS policy is configured in `api-gateway/src/main/java/com/ecommerce/gateway/config/CorsConfig.java`.

### Mini exercise
- Test a browser preflight for an Authorization-header request from an allowed origin and from a disallowed origin.

### Failure scenario
- **Symptom:** The API returns 200 in Postman but the browser blocks the response.
- **Cause:** CORS response headers are missing, duplicated, or sent by both gateway and service with incompatible values.
- **Fix:** Make the gateway the single source of browser-facing CORS policy and use a specific origin allowlist.

### Key takeaway
- CORS is enforced by browsers, not by Postman or server-to-server clients.
- Preflight requests require explicit method and header permission.
- Never combine credentialed requests with an unrestricted origin policy.
