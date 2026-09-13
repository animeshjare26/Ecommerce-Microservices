# Deep Dive 02: JWT Anatomy & Signature Mathematics

> **Module:** `05-security-and-identity`  
> **Target Audience:** From Beginner Intern to Principal Architect  
> **Core Concept:** Base64Url Encoding, Header, Payload/Claims, Cryptographic Signatures, Tampering Detection.

> **Status:** Implemented
>
> **Related code:** `user-service/src/main/java/com/ecommerce/user/security/jwt/JwtUtils.java`
>
> **Last verified against:** Spring Boot 3.3.2 / JJWT 0.12.6

---

## 🟢 Tier 1: The Intuitive Mental Model (For Beginners & Interns)

### The Notarized Contract Analogy

Imagine a 3-page legal contract:

$$\text{JWT} = \text{Page 1 (Header)} \cdot \text{Page 2 (Payload)} \cdot \text{Page 3 (Signature)}$$

- **Page 1 (Header):** States the language and notary stamp rules (`{"alg": "RS256", "typ": "JWT"}`).
- **Page 2 (Payload / Claims):** The actual agreement:
  `"Alice is User #42. She has ROLE_USER permissions. This agreement expires in 15 minutes."`
- **Page 3 (Signature):** The Government Notary's stamped seal.

#### Why Can't Alice Tamper With It?
Suppose Alice uses White-Out on Page 2, changes `"ROLE_USER"` to `"ROLE_ADMIN"`, and hands it to a bank teller:
- The teller calculates the signature of Page 1 + Page 2 using the Notary's Public Key.
- Because Alice changed a single letter on Page 2, **the mathematical checksum produces a totally different signature!**
- The teller immediately rejects the contract:
  ❌ *"The signature doesn't match the pages! Tampering detected!"* $\rightarrow$ **HTTP 401 Unauthorized!**

```
eyJhbGciOiJSUzI1NiJ9 . eyJzdWIiOiJhbGljZSIs... . P7fQx8zY1...
─────────────────────   ────────────────────────   ─────────────
   Part 1: Header           Part 2: Payload         Part 3: Signature
 (Base64Url of JSON)      (Base64Url of Claims)   (Cryptographic Hash)
```

---

## 🟡 Tier 2: Clearing Common Doubts & Anti-Patterns

### Doubt 1: "Is Base64Url the same as standard Base64?"
**Almost, but with two critical differences for HTTP URLs:**
- Standard Base64 uses `+`, `/`, and `=` (padding).
- In HTTP URLs and headers, `+` is interpreted as a space, `/` is a path separator, and `=` conflicts with query parameters!
- **Base64Url Modification (RFC 4648):**
  - Replaces `+` with `-` (minus).
  - Replaces `/` with `_` (underscore).
  - Strips `=` padding completely.
This guarantees that a JWT can be safely transmitted in HTTP headers, cookies, and URL query strings without URL-encoding corruption!

### Doubt 2: "Can an attacker decode my JWT?"
**YES! In 1 second!**
Base64Url is an **encoding format**, NOT encryption. Anyone with a browser can open DevTools, copy the token, and paste it into `jwt.io` to read every claim.
- **Rule:** Never store passwords, PINs, or confidential database connection strings inside a JWT!

---

## 🔴 Tier 3: Low-Level Internal Mechanics (For Senior Engineers)

### The Mathematical Signature Verification Flow

```
1. Header JSON:  {"alg":"RS256","typ":"JWT"}  ──► Base64Url ──► "eyJhbGci..." (Segment 1)
2. Payload JSON: {"sub":"alice","userId":42}   ──► Base64Url ──► "eyJzdWIi..." (Segment 2)

3. Signing Input String:
   signingInput = Segment1 + "." + Segment2

4. Generating Signature (user-service):
   Signature = RSA_SIGN( SHA256(signingInput), RSAPrivateKey )

5. Verifying Signature (api-gateway):
   DecryptedHash = RSA_VERIFY( Signature, RSAPublicKey )
   ExpectedHash  = SHA256( Segment1 + "." + Segment2 )

   Is DecryptedHash == ExpectedHash ?
   ├── YES ──► Valid! Allow request.
   └── NO  ──► TAMPERED! Throw SignatureException.
```

---

## 🏆 Tier 4: Top 5 Tricky Tier-1 Interview Questions & Winning Answers

### Q1: What are the 7 Standard Registered Claims defined in RFC 7519?
**Answer:**
1. `iss` (Issuer): Who created and signed the token (e.g. `https://auth.ecommerce.com`).
2. `sub` (Subject): The principal identifier (e.g. user email or UUID).
3. `aud` (Audience): Who the token is intended for (e.g. `ecommerce-api`).
4. `exp` (Expiration Time): Unix epoch timestamp when token becomes invalid.
5. `nbf` (Not Before): Unix epoch timestamp before which token must not be accepted.
6. `iat` (Issued At): Unix epoch timestamp when token was created.
7. `jti` (JWT ID): Globally unique identifier for this specific token instance.

### Q2: What is the "None" Algorithm vulnerability (CVE-2015-9235)?
**Answer:**
The JWT specification originally permitted `"alg": "none"` for unsigned debugging tokens.
- **The Attack:** An attacker took a valid token, changed their role to `admin`, altered the header to `{"alg": "none"}`, stripped the signature segment, and sent `header.payload.`.
- Naive early libraries checked the header algorithm, saw `none`, skipped signature validation, and granted admin access!
- **Modern JJWT Defense:** Modern libraries (like JJWT 0.12.6) explicitly reject `none` algorithm tokens unless configured in special insecure test modes.

### Q3: How do you prevent JSON injection in JWT claims?
**Answer:**
By using a robust, type-safe serializer (like Jackson via `jjwt-jackson`) rather than manual string concatenation (`"{\"sub\":\"" + email + "\"}"`). If email contains unescaped quotes (`alice","roles":["ADMIN`), manual string building allows claims injection!

### Q4: What is the difference between JWS, JWE, and JWT?
**Answer:**
- **JWT (JSON Web Token):** The overarching standard (RFC 7519) representing claims as JSON.
- **JWS (JSON Web Signature - RFC 7515):** A JWT whose payload is **digitally signed** for integrity (our implementation).
- **JWE (JSON Web Encryption - RFC 7516):** A JWT whose payload is **encrypted** using symmetric or asymmetric ciphers for confidentiality (cannot be read without the private decryption key).

### Q5: How large can a JWT get before it causes HTTP server failures?
**Answer:**
Most web servers and load balancers have a **default HTTP header limit of 8KB** (Tomcat default: `server.max-http-request-header-size = 8192`).
- If an enterprise packs 50 user permissions, 20 tenant IDs, and nested company attributes into a JWT, the token can swell to 10KB.
- In-flight requests will be abruptly rejected by NGINX or Tomcat with **HTTP 431 Request Header Fields Too Large**!
- **Solution:** Keep JWT claims minimal (`userId`, `roles`, `jti`), and fetch granular permissions from an in-memory cache when needed.

---

## 🛠️ Tier 5: Apply It in This Repository

### Where it appears
- JWT generation and validation are implemented in `user-service/src/main/java/com/ecommerce/user/security/jwt/JwtUtils.java`.

### Mini exercise
- Decode a locally issued token, identify `sub`, `iat`, `exp`, `jti`, `userId`, and `roles`, then verify why changing one claim invalidates its signature.

### Failure scenario
- **Symptom:** Requests fail with HTTP 431 or a proxy rejects an Authorization header.
- **Cause:** Too many roles, permissions, or profile fields were embedded in the JWT.
- **Fix:** Keep claims small and retrieve detailed authorization data separately.

### Key takeaway
- Base64Url encoding is not encryption.
- A signature protects integrity and authenticity.
- `exp`, issuer, audience, and algorithm validation define a token's trust boundary.
