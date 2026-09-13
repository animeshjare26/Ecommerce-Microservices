# Deep Dive 04: Why Do We Need JTI & Token Revocation?

> **Module:** `05-security-and-identity`  
> **Target Audience:** From Beginner Intern to Principal Architect  
> **Core Concept:** RFC 7519 `jti` Claim, Single-Device Logout, Replay Attack Defense, Redis TTL Blocklist.

> **Status:** Partially implemented — refresh-token tracking is implemented; gateway access-token blocklisting is planned.
>
> **Related code:** `user-service/src/main/java/com/ecommerce/user/service/impl/AuthServiceImpl.java`, `user-service/src/main/java/com/ecommerce/user/repository/RefreshTokenRepository.java`
>
> **Last verified against:** Spring Boot 3.3.2 / JJWT 0.12.6

---

## 🟢 Tier 1: The Intuitive Mental Model (For Beginners & Interns)

### The Credit Card vs. Hologram Analogy

To understand why `jti` is essential even when you have RSA cryptography:

- **The RSA Signature** is the **Hologram Seal** printed on a credit card. It proves that Visa or Mastercard genuinely minted this physical card and it is not a counterfeit piece of plastic.
- **The Expiration Date (`exp`)** is the expiry date embossed on the card (`Valid Thru 12/26`).
- **The `jti` (JWT ID)** is the **unique 16-digit card number**!

Now, imagine your credit card is lost or stolen at a restaurant:
- The card still has a 100% genuine holographic seal (valid RSA signature).
- The card is not expired (today is not 12/26).
- **How does the bank prevent the thief from using it?**
- The bank **blacklists that specific 16-digit number (`jti`)** in their central database!
- When the terminal swipes the card, it checks: *"Is card number 4111-2222 on the stolen list?"* If yes $\rightarrow$ **DECLINED!**

```
┌─────────────────────────────────────────────────────────────────────────┐
│ WHAT HAPPENS WITHOUT JTI:                                               │
│ User logs into Laptop (Session 1) and Phone (Session 2).                │
│ User clicks "Log Out" on Laptop.                                        │
│ How does the server revoke Session 1?                                   │
│ ❌ If you revoke by userId: User is kicked out of their Phone too!      │
│ ❌ If you do nothing: Attacker on laptop can use token for 15 minutes! │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│ WHAT HAPPENS WITH JTI:                                                  │
│ Laptop Token: { "sub": "alice", "userId": 42, "jti": "uuid-AAA" }       │
│ Phone Token:  { "sub": "alice", "userId": 42, "jti": "uuid-BBB" }       │
│ User logs out of Laptop:                                                │
│ Redis command: SET blocklist:jti:uuid-AAA "revoked" EX 900              │
│ ✅ Gateway rejects "uuid-AAA" in < 0.5ms!                                │
│ ✅ Phone session ("uuid-BBB") remains fully active and working!         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 🟡 Tier 2: Clearing Common Doubts & Anti-Patterns

### Doubt 1: "Doesn't using a Redis blocklist defeat the purpose of 'stateless' JWTs?"
**No, it is a pragmatic engineering hybrid!**
- In a traditional **stateful session** system: The server must read session data on **every request** to know who the user is. If session storage goes down, nobody is authenticated.
- In our **stateless + blocklist** system:
  1. The token itself tells the Gateway who the user is (`userId`, `email`, `roles`).
  2. The blocklist stores **ONLY revoked tokens**, NOT active sessions!
  3. 99.9% of users never log out prematurely during a 15-minute window; their tokens pass through without hitting any database.
  4. If Redis goes down temporarily, the Gateway can fall back to cryptographic verification as a fail-safe!

### Doubt 2: "Won't the Redis blocklist grow infinitely and consume all memory?"
**No, because of automatic Redis TTL (Time-To-Live)!**
When writing to Redis, we set the TTL equal to the **remaining lifespan of that specific token**:
$$\text{TTL} = \text{Token Expiration Timestamp} - \text{Current Timestamp}$$
If a token expires in 8 minutes, the Redis key is set to expire in 480 seconds:
```text
SET blocklist:jti:c1f7a2d4-3b1a... "revoked" EX 480
```
Once 8 minutes pass, the token's cryptographic signature naturally expires, and Redis automatically purges the key from memory! The blocklist only holds tokens that are currently active but revoked.

### Doubt 3: "Why not just revoke by `userId` instead of `jti`?"
Revoking by `userId` is appropriate **only for nuclear actions**:
- Admin bans a fraudulent user account.
- User changes their password (forced global logout on all devices).
For normal everyday logouts, revoking by `userId` destroys user experience by kicking the customer off their mobile phone and tablet when they only intended to log out on a work computer.

---

## 🔴 Tier 3: Low-Level Internal Mechanics (For Senior Engineers)

### Replay Attack Prevention

In financial workflows, OAuth2 code authorization, or password resets, a token must have a **single-use guarantee**:

```
Client ──(1) POST /orders/checkout (Token with jti: 8a2f)──► [ API Gateway ] ──► [ Order Service ]
                                                                   │
                                                                   ▼ (Atomic Redis Op)
                                                      SET order:jti:8a2f "consumed" NX EX 60
                                                                   │
                                                                   ├── If "OK" ──► Proceed!
                                                                   └── If (nil) ──► 409 Conflict: REPLAY ATTACK!
```

- Using Redis `SET ... NX` (Set if Not Exists) executes atomically.
- If a man-in-the-middle captures the HTTP packet and replays the request, the second execution returns `nil` and is rejected immediately.

---

## 🏆 Tier 4: Top 5 Tricky Tier-1 Interview Questions & Winning Answers

### Q1: What is the official RFC specification for `jti`?
**Answer:**
Section 4.1.7 of **RFC 7519** defines `jti` (JWT ID):
> *"The 'jti' (JWT ID) claim provides a unique identifier for the JWT. The identifier value MUST be assigned in a manner that ensures that there is a negligible probability that the same value will be accidentally assigned to a different data object... The 'jti' claim can be used to prevent the JWT from being replayed."*
In production, standard Version 4 (random) UUIDs (`java.util.UUID.randomUUID()`) are used.

### Q2: What is the difference between Token Revocation and Token Blacklisting?
**Answer:**
- **Token Blacklisting (Blocklist):** Proactive negative list. All tokens are assumed valid by default unless their `jti` exists in the blocklist. Best for short-lived access tokens.
- **Token Whitelisting (Allowlist):** Strict positive list. A token is invalid unless its `jti` is explicitly found in a persistent database table. Best for long-lived Refresh Tokens (as implemented in our `user-service` `refresh_tokens` table).

### Q3: How do you handle clock skew between microservice servers when validating `exp` and `jti`?
**Answer:**
In distributed systems, physical server clocks drift by milliseconds or seconds. If Gateway's clock is 5 seconds ahead of Auth Service's clock, newly issued tokens might be rejected as "not yet valid" (`nbf` claim) or expired prematurely.
- **Solution:** JJWT provides clock skew tolerance:
  ```java
  Jwts.parser().clockSkewSeconds(60).verifyWith(...).build();
  ```
  Setting a 60-second clock skew tolerance prevents false rejections caused by minor NTP drift.

### Q4: If Redis goes down, should the API Gateway reject all requests or allow them through? (Fail-Open vs. Fail-Closed)
**Answer:**
This is a business-critical trade-off:
- **Fail-Closed (Financial / Banking):** If Redis is down, reject requests to protected routes with 503. Security takes precedence over availability.
- **Fail-Open (E-Commerce / Social Media):** If Redis is down, log an error alarm and verify the token using **cryptographic RS256 signature only**. The worst case is a recently revoked token works for a few minutes; the benefit is millions of genuine customers can continue shopping without an outage! In our Gateway, we implement Fail-Open with `onErrorResume`.

### Q5: How does `jti` facilitate SOC 2 / ISO 27001 compliance?
**Answer:**
Compliance standards require non-repudiation and auditable access logs. Because a `userId` may have hundreds of requests across days, logging only `userId` does not prove which client device originated a specific data modification. Associating every API write operation with an immutable `jti` provides a forensically verifiable audit trail that traces directly back to the exact login event and IP address.

---

## 🛠️ Tier 5: Apply It in This Repository

### Where it appears
- Refresh-token identifiers are stored in `user-service/src/main/java/com/ecommerce/user/entity/RefreshToken.java` and checked by `AuthServiceImpl.java`.

### Mini exercise
- Implement an atomic access-token blocklist lookup with Redis TTL equal to the token's remaining lifetime.

### Failure scenario
- **Symptom:** A stolen token can still perform a sensitive operation after logout.
- **Cause:** The system has an identifier but never checks a revocation or consumed-token store.
- **Fix:** Use a TTL blocklist for access-token revocation or an allowlist for single-use/long-lived tokens.

### Key takeaway
- `jti` identifies a token; it does not revoke it by itself.
- Blocklists fit short-lived access tokens; allowlists fit refresh tokens.
- Single-use flows need an atomic consume operation.
