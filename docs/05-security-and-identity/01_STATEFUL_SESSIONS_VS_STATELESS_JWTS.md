# Deep Dive 01: Stateful Sessions vs. Stateless JWTs

> **Module:** `05-security-and-identity`  
> **Target Audience:** From Beginner Intern to Principal Architect  
> **Reading Order:** Read FIRST before studying JWT Anatomy or Asymmetric Cryptography.

> **Status:** Implemented
>
> **Related code:** `user-service/src/main/java/com/ecommerce/user/security/SecurityConfiguration.java`
>
> **Last verified against:** Spring Boot 3.3.2 / Spring Security 6

---

## 🟢 Tier 1: The Intuitive Mental Model (For Beginners & Interns)

### The Hotel Analogy: Physical Keycards vs. Wristband QR Codes

Imagine two different hotel check-in systems:

#### 1. The Stateful Model (Physical Metal Key):
- When you check in at the front desk, the clerk gives you **Key #42**.
- The hotel manager writes in a paper ledger inside their office: *"Key #42 belongs to Alice until Friday"*.
- Every time you want to enter the swimming pool or gym, a security guard must **call the front desk**:
  *"Hey, someone with Key #42 wants into the gym. Does your ledger say Alice is allowed?"*
- **The Problem:** If the front desk clerk is overwhelmed or the office catches fire, **nobody in the entire hotel can enter the gym, pool, or dining room!**

#### 2. The Stateless Model (Tamper-Proof Holographic Wristband):
- The front desk gives you a wristband with a holographic seal printed with:
  `Name: Alice | Room: 42 | Expires: Friday | Access: Gym, Pool`
- The hotel front desk **writes nothing down in any ledger**.
- When you walk into the gym, the guard simply looks at the holographic seal to verify it wasn't forged.
- The guard reads the permissions directly off your wristband in **1 second without calling anyone!**

```
┌─────────────────────────────────────────────────────────────────────────┐
│ STATEFUL SESSIONS (Monolith Pattern):                                   │
│ Client ──(Cookie: JSESSIONID=abc123)──► Server [ Looks up RAM memory ]  │
│ ❌ Needs server memory. Fails when load balancing across 10 servers!    │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│ STATELESS JWTs (Microservices Pattern):                                 │
│ Client ──(Bearer eyJhbGciOi...)───────► Any Server [ Verifies crypto ]  │
│ ✅ Zero server memory. Infinitely scalable across 1,000 servers!         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 🟡 Tier 2: Clearing Common Doubts & Anti-Patterns

### Doubt 1: "Why can't we just use `JSESSIONID` cookies in microservices?"
In a monolithic Spring MVC app, `HttpSession` stores user data in Tomcat's local JVM heap memory (`ConcurrentHashMap`).
In microservices:
1. An AWS Application Load Balancer distributes requests across 5 instances of `user-service`.
2. Request 1 (`POST /login`) hits **Instance 1** $\rightarrow$ Session saved in Instance 1's RAM.
3. Request 2 (`GET /profile`) hits **Instance 2** $\rightarrow$ Instance 2's RAM has no record of `JSESSIONID=abc123`.
4. Result: User is mysteriously logged out!

### Doubt 2: "Can't we solve that with Sticky Sessions or Redis Session Clustering?"
Yes, but both introduce severe architectural compromises:
- **Sticky Sessions:** Forces the load balancer to always route User Alice to Instance 1. If Instance 1 crashes, Alice's session is destroyed. If an influencer with 100,000 requests hits Instance 1, load balancing fails completely!
- **Redis Session Clustering (`Spring Session`):** Solves the sticky session issue, but **every single HTTP request across all microservices must query Redis** to fetch session state, introducing network latency and an external failure dependency.

### Doubt 3: "Is a JWT encrypted? Can users see their data?"
**NO! A standard JWT is SIGNED, not encrypted!**
The payload is encoded using **Base64Url** (which is just an encoding, like ASCII or Hex). Anyone can paste a JWT into `jwt.io` and read the JSON claims.
- **Rule:** Never store sensitive private data (credit card numbers, raw passwords, social security numbers) inside a JWT payload!

---

## 🔴 Tier 3: Low-Level Internal Mechanics (For Senior Engineers)

### Memory & Scale Footprint Comparison

| Characteristic | Stateful Session (`HttpSession`) | Stateless JWT (`Bearer Token`) |
|---|---|---|
| **Server RAM Allocation** | ~2KB to 10KB per active user session in JVM heap. 100,000 users = **1GB RAM overhead**. | **0 bytes** of server RAM. |
| **GC (Garbage Collection) Pressure** | High. Constant creation and expiration of session objects in Young/Old Gen heap. | Zero GC heap footprint for session tracking. |
| **Network Payload Size** | Small (~32 bytes for `JSESSIONID` cookie). | Larger (~500 to 1,500 bytes for header + claims + signature). |
| **Cross-Domain / Mobile Friendly** | Poor (Cookies suffer from SameSite restrictions, CORS issues, mobile app cookie jar inconsistencies). | Excellent (Standard `Authorization: Bearer <jwt>` HTTP header works natively across iOS, Android, SPAs). |
| **CSRF Vulnerability** | High (Browsers automatically attach cookies to cross-origin requests). | Immune to CSRF if stored in memory and sent via `Authorization` header. |

---

## 🏆 Tier 4: Top 5 Tricky Tier-1 Interview Questions & Winning Answers

### Q1: What is the biggest disadvantage of a purely stateless JWT?
**Answer:**
**The Revocation Problem.** Because the server stores no state, an issued token is mathematically valid until its expiration timestamp (`exp`). If an employee is fired, an account is banned, or a user's phone is stolen, the server cannot unilaterally "destroy" the token without introducing some state (such as a Redis JTI blocklist).

### Q2: How do production systems balance stateless scaling with instant revocation?
**Answer:**
Via **Two-Tier Token Architecture**:
1. **Short-Lived Access Tokens (10–15 mins):** Kept purely stateless for ultra-fast in-memory cryptographic verification at the API Gateway.
2. **Long-Lived Refresh Tokens (7–30 days):** Tracked in a database/Redis allow-list. Revoking the refresh token prevents the user from obtaining new access tokens, strictly limiting the maximum exposure window to 15 minutes.
3. For immediate ban enforcement, the Gateway checks a lightweight **Redis JTI blocklist** ($< 0.5\text{ms}$).

### Q3: Why does storing a JWT in browser `localStorage` pose a security risk?
**Answer:**
`localStorage` is accessible to any JavaScript running on that domain. If your application has a Cross-Site Scripting (XSS) vulnerability (e.g. malicious code injected via an unsanitized npm package or rich text comment), an attacker can execute `localStorage.getItem("token")` and exfiltrate the JWT. In enterprise applications, tokens are often stored in an in-memory JavaScript closure, with refresh tokens stored in an `HttpOnly`, `Secure`, `SameSite=Strict` cookie.

### Q4: If JWTs are larger than session cookies, doesn't that waste bandwidth?
**Answer:**
Yes, a 1KB JWT sent across 100 API calls adds ~100KB of network ingress bandwidth. However, in modern gigabit networks and 5G mobile connections, the 100KB bandwidth cost is trivial compared to the massive gains in eliminating database/Redis lookups and unlocking horizontal scalability across hundreds of stateless microservice nodes.

### Q5: Can you sign a JWT with one algorithm and verify with another? (The Algorithm Confusion Attack)
**Answer:**
Yes! This was a famous vulnerability in early JWT libraries. If a server supports both RS256 (asymmetric) and HS256 (symmetric), an attacker can take the server's **public key** (which is publicly known), sign a forged token using **HS256** with the public key as the secret, and change the header to `{"alg": "HS256"}`. If the server naively uses the same verification method without explicitly validating the expected algorithm, it verifies the forged token! Modern JJWT 0.12.6 explicitly prevents this by separating `verifyWith(SecretKey)` and `verifyWith(PublicKey)`.

---

## 🛠️ Tier 5: Apply It in This Repository

### Where it appears
- Stateless security is configured in `user-service/src/main/java/com/ecommerce/user/security/SecurityConfiguration.java`.
- Access-token parsing occurs in `user-service/src/main/java/com/ecommerce/user/security/jwt/AuthTokenFilter.java`.

### Mini exercise
- Trace a `GET /api/users/me` request and explain where authentication is created and cleared.

### Failure scenario
- **Symptom:** A user remains authorized after being disabled or resetting a password.
- **Cause:** A previously issued access token has not expired and there is no access-token blocklist.
- **Fix:** Keep access tokens short-lived; add a blocklist or token-version check when immediate revocation is required.

### Key takeaway
- JWTs remove HTTP session state, not all security state.
- Signed JWT payloads are readable; do not put secrets in them.
- Token revocation is a deliberate availability/security trade-off.
