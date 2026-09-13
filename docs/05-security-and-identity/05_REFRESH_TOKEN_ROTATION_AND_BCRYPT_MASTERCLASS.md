# Deep Dive 05: Refresh Token Rotation & BCrypt Password Hashing

> **Module:** `05-security-and-identity`  
> **Target Audience:** From Beginner Intern to Principal Architect  
> **Core Concept:** Refresh Token Rotation (RTR), Automatic Breach Detection, BCrypt Adaptive Hashing, Cryptographic Salts.

> **Status:** Implemented
>
> **Related code:** `user-service/src/main/java/com/ecommerce/user/service/impl/AuthServiceImpl.java`
>
> **Last verified against:** Spring Boot 3.3.2 / Spring Security 6

---

## 🟢 Tier 1: The Intuitive Mental Model (For Beginners & Interns)

### Part A: The Movie Ticket Staging (Refresh Token Rotation)

Imagine buying a 30-day cinema pass:
- To enter a movie theater (API endpoint), you hand the usher a **15-minute paper wristband** (Access Token).
- When the wristband expires, you walk to the ticket booth and present your **VIP voucher ticket** (Refresh Token).
- The ticket agent stamps your VIP voucher **VOID (REVOKED)**, burns it in front of you, and hands you a **BRAND NEW VIP voucher ticket** plus a fresh wristband!
- **The Stolen Ticket Trap:**
  - If a thief stole a photocopy of your old VIP voucher and attempts to use it 10 minutes later, the agent checks their computer:
    *"Wait! This voucher was already marked VOID 10 minutes ago! Someone is using a stolen voucher!"*
  - **The Alarm Triggers:** The cinema cancels ALL tickets belonging to that account immediately!

### Part B: The Bank Vault Lock (BCrypt Password Hashing)

Why don't we store passwords using fast algorithms like SHA-256?
- **SHA-256** is designed for high-speed file verification (checking a 10GB game file in 2 seconds). A modern NVIDIA GPU can calculate **10,000,000,000 SHA-256 hashes every single second**!
- If a hacker steals your database, they can crack an 8-character password in minutes.
- **BCrypt** is intentionally designed to be **SLOW and HEAVY**:
  - It runs thousands of mathematical rounds ($2^{12} = 4,096$ rounds in our app).
  - It takes ~100 milliseconds for one password verification on the server (imperceptible to a human logging in).
  - But for a hacker trying 1 billion passwords on a GPU, it would take **hundreds of years**!

---

## 🟡 Tier 2: Clearing Common Doubts & Anti-Patterns

### Doubt 1: "Why do we need a database table for Refresh Tokens if JWTs are stateless?"
Because **Refresh Tokens represent Long-Term Authorization (Days or Weeks)**:
- If a user's phone is stolen on day 3 of a 30-day session, you must have the ability to revoke their access.
- In our `user-service`, `refresh_tokens` are stored in PostgreSQL (`token_jti`, `user_email`, `expires_at`, `is_revoked`).
- Every time a user requests a new Access Token (`POST /auth/refresh`), `user-service` validates the signature, checks that `is_revoked == false`, immediately revokes that token, and saves a brand new one!

### Doubt 2: "Is BCrypt encryption?"
**NO! BCrypt is a ONE-WAY CRYPTOGRAPHIC HASHING ALGORITHM, not encryption!**
- Encryption is reversible (Data $\rightarrow$ Ciphertext $\rightarrow$ Decrypt with key $\rightarrow$ Data).
- Hashing is strictly one-way (Password $\rightarrow$ Hash). You **cannot "decrypt" a BCrypt hash** back into the original password.
- When a user logs in, BCrypt hashes their entered password with the stored salt, and checks if the resulting hash matches the stored string (`passwordEncoder.matches(raw, hash)`).

---

## 🔴 Tier 3: Low-Level Internal Mechanics (For Senior Engineers)

### Anatomy of a BCrypt Hash String

```
$2a$12$e8kS0wJ3WvQe.L4vLz1e..9bY8a1K2j3L4m5N6o7P8q9R0s1T2u3V
─── ── ────────────────────── ──────────────────────────────
 │   │           │                          │
 │   │           │                          └── 31-character checksum / hash
 │   │           └── 22-character embedded random salt (128 bits)
 │   └── Cost factor (Work factor: 2^12 = 4,096 rounds)
 └── Algorithm identifier (2a = modern BCrypt)
```

1. **Embedded 128-bit Salt:**
   - Notice that the salt is **embedded directly in the string**!
   - You do NOT need a separate `salt` column in your PostgreSQL table.
   - When calling `matches(rawPassword, storedHash)`, BCrypt automatically extracts the salt from characters 8–29 and applies it to the raw password.
2. **Rainbow Table Immunity:**
   - A Rainbow Table is a precomputed lookup table of billions of common password hashes.
   - Because BCrypt generates a unique 128-bit cryptographically secure random salt for every user, two users with the exact same password (`Password123!`) produce **completely different hash strings** in the database!

---

## 🏆 Tier 4: Top 5 Tricky Tier-1 Interview Questions & Winning Answers

### Q1: How does Refresh Token Rotation detect and stop token theft?
**Answer:**
Under Refresh Token Rotation, each refresh token can be used **exactly once**:
1. User logs in $\rightarrow$ Receives `RT_1`.
2. Attacker steals `RT_1`.
3. User legitimately refreshes $\rightarrow$ Presents `RT_1`. Server revokes `RT_1` and issues `RT_2` to the user.
4. Later, Attacker presents `RT_1`.
5. **The Trap:** Server detects `RT_1` has already been revoked (`is_revoked == true`)!
6. **Breach Protocol:** The server recognizes a **Token Replay / Theft condition**, immediately marks all active refresh tokens for that user account as revoked, and forces a re-login on all devices!

### Q2: What is the recommended BCrypt work factor in modern cloud environments?
**Answer:**
Between **10 and 12**:
- Work factor 10: $2^{10} = 1,024$ rounds (~50ms per check).
- Work factor 12 (Our configuration): $2^{12} = 4,096$ rounds (~100ms per check).
- If the work factor is too low (< 8), GPUs can brute-force hashes. If set too high (> 14), verifying a password consumes ~1.5 seconds of CPU time, allowing attackers to mount a CPU-starvation Denial of Service (DoS) attack by sending 100 simultaneous login attempts!

### Q3: Why is Argon2 considered superior to BCrypt in modern cryptographic contests?
**Answer:**
Argon2 (winner of the Password Hashing Competition in 2015) is **memory-hard**, meaning it requires large amounts of dedicated RAM (e.g. 64MB) per hash calculation, making it mathematically impossible to parallelize effectively on GPUs and ASICs (which have limited memory per thread). BCrypt is CPU-hard but uses relatively small RAM (4KB Blowfish state). In Spring Security, Argon2 is supported via `Argon2PasswordEncoder`.

### Q4: Should Refresh Tokens be stored in an HttpOnly cookie or in the JSON response body?
**Answer:**
In production web applications, **`HttpOnly`, `Secure`, `SameSite=Strict` cookies** are the gold standard:
- An `HttpOnly` cookie **cannot be read by JavaScript** (`document.cookie` returns empty), making it completely immune to theft via Cross-Site Scripting (XSS).
- The `SameSite=Strict` attribute prevents Cross-Site Request Forgery (CSRF).
- The Access Token remains in JavaScript memory for fast API calls.

### Q5: How do you migrate from BCrypt to Argon2 on an existing production database of 10 million users?
**Answer:**
Via Spring Security's **`DelegatingPasswordEncoder`**!
1. Mark `Argon2` as the default encoder for all new hashes.
2. When an existing user logs in with their old BCrypt password, `DelegatingPasswordEncoder` detects the `$2a$` prefix and verifies using BCrypt.
3. Once verified, Spring calls `passwordEncoder.upgradeEncoding(hash)`. If true, the application transparently re-hashes the raw password using Argon2 and updates the PostgreSQL row! Over time, active users are upgraded without requiring forced password resets.

---

## 🛠️ Tier 5: Apply It in This Repository

### Where it appears
- Login, refresh-token rotation, password reset, and BCrypt hashing are handled by `user-service/src/main/java/com/ecommerce/user/service/impl/AuthServiceImpl.java`.

### Mini exercise
- Write a test proving that a rotated refresh token cannot be used a second time.

### Failure scenario
- **Symptom:** Two simultaneous refresh requests both receive new token pairs.
- **Cause:** The implementation reads and revokes the old token without an atomic conditional update or lock.
- **Fix:** Revoke with `WHERE token_jti = ? AND is_revoked = false` and proceed only if one row changed.

### Key takeaway
- Passwords are hashed, never encrypted.
- Refresh rotation limits replay but needs atomic persistence.
- Revoking refresh tokens does not immediately revoke existing access tokens.
