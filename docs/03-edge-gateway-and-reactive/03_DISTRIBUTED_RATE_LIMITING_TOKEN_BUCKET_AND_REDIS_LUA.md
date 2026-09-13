# Deep Dive 03: Distributed Rate Limiting: Token Bucket & Redis Lua Scripts

> **Module:** `03-edge-gateway-and-reactive`  
> **Target Audience:** From Beginner Intern to Principal Architect  
> **Core Concept:** Token Bucket Algorithm, Leaky Bucket, Fixed Window Spikes, Redis Atomic Lua Scripts.

---

## 🟢 Tier 1: The Intuitive Mental Model (For Beginners & Interns)

### The Arcade Game Token Machine Analogy

Imagine an arcade with a token dispenser machine:

#### 1. The Token Bucket:
- The arcade machine has a bucket that can hold at most **20 tokens** (`burstCapacity`).
- Every second, a conveyor belt drops **10 fresh tokens** into the bucket (`replenishRate`).
- If nobody plays, tokens overflow and spill onto the floor (the bucket stops at 20; it cannot hold more).
- To play an arcade game (send an HTTP request), you must take **1 token** from the bucket.

#### 2. What Happens During a Burst?
- A group of 15 friends walks in simultaneously.
- Because the bucket holds 20 tokens, all 15 friends can immediately grab a token and play (**Permits legitimate bursts!**).
- Now the bucket has 5 tokens left.
- If 10 more people rush in immediately, the first 5 play, but the last 5 find the bucket empty:
  ❌ *"Out of tokens! Please wait 1 second for fresh tokens to drop!"* $\rightarrow$ **HTTP 429 Too Many Requests!**

```
                  ┌─────────────────────────────────┐
                  │ 🪙  REPLENISHMENT CONVEYOR       │
                  │ +10 tokens every 1 second       │
                  └────────────────┬────────────────┘
                                   │
                                   ▼
                ┌─────────────────────────────────────┐
                │ 🪣 TOKEN BUCKET (Max: 20 Tokens)    │
                │   🪙 🪙 🪙 🪙 🪙 🪙 🪙 🪙 🪙 🪙     │
                └──────────────────┬──────────────────┘
                                   │
                      -1 token per HTTP request
                                   ▼
                ┌─────────────────────────────────────┐
                │ [ Request Allowed: 200 OK ]         │
                │ (If bucket empty: 429 Too Many Req!)│
                └─────────────────────────────────────┘
```

---

## 🟡 Tier 2: Clearing Common Doubts & Anti-Patterns

### Doubt 1: "Why not use a simple Fixed Window counter (e.g. 100 requests per minute)?"
The **Fixed Window Boundary Spike Trap**:
- Suppose your limit is 100 requests per minute.
- A malicious user sends 100 requests at **00:59**.
- A new minute begins at **01:00**, resetting their counter to 0.
- The user sends another 100 requests at **01:01**.
- **Result:** The user sent **200 requests in 2 seconds!**
- The backend database crashes under the spike, even though the user technically stayed within their 100 req/min window!
- **The Token Bucket eliminates this flaw completely.**

### Doubt 2: "What is the difference between Token Bucket and Leaky Bucket?"
- **Leaky Bucket:** Water enters at irregular rates but leaks out of a hole at a **strict constant rate** (e.g. exactly 10 req/sec, no faster). Any burst above the constant leak rate is instantly dropped or delayed.
- **Token Bucket:** Allows users to burst up to the bucket's capacity (`burstCapacity = 20`) without any delay, as long as their long-term average throughput does not exceed the replenishment rate (`replenishRate = 10`). Token Bucket is far superior for modern web and mobile apps that load multiple images/scripts concurrently!

---

## 🔴 Tier 3: Low-Level Internal Mechanics (For Senior Engineers)

### Why Redis Lua Scripts Are Mandatory in Distributed Systems

In a production environment, you run **5 instances of the API Gateway** behind an AWS ALB.

#### The Race Condition Failure (Without Lua):
If Gateway 1 and Gateway 2 both try to decrement the bucket using standard Redis commands (`GET` followed by `SET`):

```
Time  Gateway 1                     Gateway 2                     Redis Bucket Value
────  ─────────                     ─────────                     ──────────────────
T1    GET user:42 (reads 1 token)                                 Count = 1
T2                                  GET user:42 (reads 1 token)   Count = 1
T3    SET user:42 (decrements to 0)                               Count = 0
T4                                  SET user:42 (decrements to 0) Count = 0
```
- Both gateways allowed the request, granting **2 requests when only 1 token remained!**

#### The Redis Lua Solution (Atomic Execution):
Spring Cloud Gateway bundles an optimized Lua script (`request_rate_limiter.lua`).
- Redis is single-threaded for command execution.
- When the Gateway sends a Lua script via `EVALSHA`, **Redis executes the entire script atomically in a single CPU cycle**.
- The script:
  1. Reads the last replenishment timestamp.
  2. Calculates how many tokens to add based on elapsed time:
     $$\Delta \text{tokens} = (\text{now} - \text{last\_replenished}) \times \text{replenishRate}$$
  3. Caps tokens at `burstCapacity`.
  4. Checks if tokens $\ge 1$:
     - If yes: decrements tokens by 1, updates timestamp, and returns `1` (ALLOWED).
     - If no: returns `0` (DENIED).
- Zero race conditions, zero distributed locks, sub-millisecond execution!

---

## 🏆 Tier 4: Top 5 Tricky Tier-1 Interview Questions & Winning Answers

### Q1: What HTTP headers should a compliant API Gateway return when rate limiting?
**Answer:**
According to IETF RFC 6585 and RFC 7231 standards:
1. `X-RateLimit-Remaining`: How many tokens are left in the user's bucket.
2. `X-RateLimit-Burst-Capacity`: The maximum bucket capacity.
3. `X-RateLimit-Replenish-Rate`: How many tokens are added per second.
4. `Retry-After`: When a request is rejected with `429 Too Many Requests`, this header informs the client how many seconds to wait before attempting another request.

### Q2: What is the difference between IP-based vs. User-based Rate Limiting?
**Answer:**
- **IP-Based (Anonymous):** Rate limits based on the client's IP address (`remoteAddress`).
  - *Risk:* Multiple legitimate users sharing a corporate NAT, university campus, or VPN share a single IP and can starve each other.
- **User-Based (Authenticated):** Rate limits based on the verified `X-User-Id` from the JWT token.
  - *Advantage:* Fair usage per paying account regardless of what network or Wi-Fi they use.
  - *Best Practice:* Use IP-based for public `/auth/**` endpoints, and User-based for protected `/api/**` routes (as implemented in our `RateLimiterConfig.java`).

### Q3: How do you protect against "Distributed Denial of Service" (DDoS) where attackers use 100,000 unique botnet IPs?
**Answer:**
IP-based rate limiting at the Application Gateway is insufficient for volumetric DDoS (e.g. 100Gbps SYN floods or botnet waves).
- **Defense in Depth:** Production architectures place **AWS CloudFront + AWS WAF (Web Application Firewall)** or **Cloudflare** in front of the API Gateway. Cloudflare uses machine-learning bot reputation scoring, CAPTCHA challenges, and edge Anycast IP scrubbing before packets can even reach the Gateway!

### Q4: How does a Sliding Window Log rate limiter compare to Token Bucket?
**Answer:**
- **Sliding Window Log:** Stores every request timestamp in a Redis Sorted Set (`ZSET`). It counts timestamps in the range `(now - 60s, now)`.
  - *Advantage:* 100% accurate sliding window.
  - *Disadvantage:* High memory consumption ($O(N)$ where $N$ is the number of requests).
- **Token Bucket:** Stores only 2 numbers in Redis: `tokens_left` and `last_updated_timestamp` ($O(1)$ constant memory). This makes Token Bucket drastically more memory-efficient at scale!

### Q5: How do you handle "Thundering Herd" retry storms when thousands of clients hit 429 simultaneously?
**Answer:**
When clients receive 429, naive clients immediately retry after exactly 1 second, causing a cyclical wave of traffic that crashes the system repeatedly.
- **Solution:** Clients must implement **Exponential Backoff with Full Jitter** (AWS architecture pattern):
  $$\text{WaitTime} = \text{random}(0, 2^{\text{attempt}} \times \text{base\_delay})$$
  Adding randomness ("jitter") spreads out retry requests smoothly over time!
