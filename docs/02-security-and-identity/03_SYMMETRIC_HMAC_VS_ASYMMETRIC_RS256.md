# Deep Dive 03: Symmetric HMAC-SHA256 vs. Asymmetric RS256

> **Module:** `02-security-and-identity`  
> **Target Audience:** From Beginner Intern to Principal Architect  
> **Core Concept:** Cryptographic Signing, Zero-Trust Architecture, PKCS#8 vs. X.509.

---

## 🟢 Tier 1: The Intuitive Mental Model (For Beginners & Interns)

### The Wax Seal & Stamp Analogy

Imagine two ways a medieval king signs royal decrees:

#### 1. The Symmetric Approach (Shared Rubber Stamp):
- The King gives a rubber stamp that says `"APPROVED BY THE KING"` to his Governor, his Tax Collector, and his Border Guard.
- Every official can stamp documents.
- **The Critical Flaw:** Because the Border Guard holds the stamp to **verify** documents, an untrustworthy Border Guard can secretly **stamp forged decrees** granting themselves gold!
- **Symmetric Encryption (HMAC):** The key used to **create/sign** the token is identical to the key used to **verify** it.

#### 2. The Asymmetric Approach (King's Personal Seal vs. Public Wax Impression):
- The King has a unique, heavily guarded signet ring (**The Private Key**). Only the King can press it into hot wax to make the seal.
- The Border Guard receives only a bronze mold of the seal (**The Public Key**).
- The Border Guard can test if an incoming wax seal fits the mold perfectly (**Verification**).
- But the Border Guard **cannot make new seals**, because they do not have the King's ring!

```
┌─────────────────────────────────────────────────────────────────────────┐
│ SYMMETRIC HMAC-SHA256 (Shared Secret):                                  │
│ [ user-service ] ───(Signs with Secret: "super-secret-123")───► Token   │
│ [ api-gateway  ] ◄──(Verifies with SAME: "super-secret-123")───┘        │
│ ⚠️ If Gateway is compromised, attacker can forge ANY token as ADMIN!   │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│ ASYMMETRIC RS256 (Private/Public Keypair):                              │
│ [ user-service ] ───(Signs with PRIVATE KEY: private_key.pem)──► Token  │
│ [ api-gateway  ] ◄──(Verifies with PUBLIC KEY: public_key.pem)──┘       │
│ 🛡️ Even if Gateway is hacked, public key CANNOT forge new tokens!      │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 🟡 Tier 2: Clearing Common Doubts & Anti-Patterns

### Doubt 1: "Does RS256 encrypt the JWT payload?"
**NO!** 
Neither HMAC nor RS256 encrypts the token. They only produce a **digital signature**.
- In HMAC: $\text{Signature} = \text{HMAC-SHA256}(\text{Header} + "." + \text{Payload}, \text{Secret})$
- In RS256: $\text{Signature} = \text{Encrypt}_{\text{PrivateKey}}(\text{SHA256}(\text{Header} + "." + \text{Payload}))$
Anyone with the token can still decode the Base64Url payload and read `sub`, `userId`, and `roles`. The signature proves only **authenticity and integrity**.

### Doubt 2: "If HMAC is faster, why would anyone use RS256?"
Because in enterprise microservices, **infrastructure security boundaries matter**:
- In modern companies, the API Gateway is deployed in a public-facing DMZ (Demilitarized Zone) or public subnet.
- The Identity Service (`user-service`) resides deep inside an isolated private subnet behind firewalls.
- If the Gateway held the shared HMAC secret, any Remote Code Execution (RCE) vulnerability in the Gateway exposes the master signing secret of the entire company!
- With RS256, the public key is **designed to be public**. Compromising the Gateway yields zero ability to sign rogue tokens.

### Doubt 3: "Do all microservices need the Public Key?"
**No!** In our architecture:
1. `api-gateway` holds `public_key.pem` and verifies the token at the edge.
2. The Gateway extracts `userId` and `roles`, and injects them into downstream HTTP headers (`X-User-Id: 42`).
3. Downstream services (`product-service`, `cart-service`, `order-service`) don't need any cryptographic keys at all! They simply read `X-User-Id` from trusted internal gateway traffic.

---

## 🔴 Tier 3: Low-Level Internal Mechanics (For Senior Engineers)

### Cryptographic Key Specs & PEM Formatting

```
Private Key (PKCS#8):                      Public Key (X.509 SubjectPublicKeyInfo):
-----BEGIN PRIVATE KEY-----                -----BEGIN PUBLIC KEY-----
MIIEvgIBADANBgkqhkiG9w0BAQEFAASCB...       MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...
-----END PRIVATE KEY-----                  -----END PUBLIC KEY-----
```

1. **PKCS#8 Format:**
   - Standard syntax for private keys.
   - Encoded as an ASN.1 sequence containing algorithm identifier (RSA `1.2.840.113549.1.1.1`) and the private key components (modulus $n$, public exponent $e$, private exponent $d$, primes $p$ and $q$).
   - In Java: Parsed using `KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes))`.

2. **X.509 Format:**
   - Standard syntax for public keys and certificates.
   - Encoded as an ASN.1 `SubjectPublicKeyInfo` containing algorithm OID and public key bits (modulus $n$ and public exponent $e = 65537$).
   - In Java: Parsed using `KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes))`.

### Computational Performance Comparison

| Metric | HMAC-SHA256 (Symmetric) | RS256 (RSA 2048-bit Asymmetric) |
|---|---|---|
| **Underlying Math** | SHA-256 bitwise XOR & compression rounds. | Modular exponentiation: $S = M^d \pmod n$ (signing), $M = S^e \pmod n$ (verifying). |
| **Verification Latency** | $\approx 10 - 20\ \mu\text{s}$ (microseconds) | $\approx 50 - 100\ \mu\text{s}$ ($< 0.1\text{ms}$) |
| **CPU Cycles per Op** | Low (hundreds of cycles) | Moderate (tens of thousands of cycles) |
| **Key Size** | 256 bits (32 bytes) | 2048 bits (256 bytes) |
| **JWT Signature Length** | 43 characters (Base64Url of 32 bytes) | 342 characters (Base64Url of 256 bytes) |

---

## 🏆 Tier 4: Top 5 Tricky Tier-1 Interview Questions & Winning Answers

### Q1: What is the mathematical relationship between the Public and Private Key in RSA?
**Answer:**
RSA relies on the mathematical difficulty of factoring the product of two large prime numbers $p$ and $q$ ($n = p \times q$).
- The **Public Key** consists of $(n, e)$, where $e$ is typically $65537$.
- The **Private Key** consists of $(n, d)$, where $d$ is the modular multiplicative inverse of $e \pmod{\lambda(n)}$.
Deriving $d$ from $(n, e)$ requires computing Euler's totient $\phi(n) = (p - 1)(q - 1)$, which is impossible without knowing the prime factors $p$ and $q$. For a 2048-bit key, factoring $n$ would require billions of years of modern computing time.

### Q2: What is the JWKS (JSON Web Key Set) endpoint and why do enterprise systems use it?
**Answer:**
A standard endpoint (typically `/.well-known/jwks.json` or `/oauth2/jwks`) defined in RFC 7517. Instead of copying PEM files across servers, the Identity Provider publishes its public keys as a JSON structure containing `kty: "RSA"`, `use: "sig"`, `kid: "key-id-2026"`, `n: "..."`, and `e: "AQAB"`.
- **Benefit:** When rotating keys, the Identity Provider publishes a new key with a new `kid`. The API Gateway downloads and caches the keys automatically, enabling **zero-downtime key rotation without restarting or redeploying any service**.

### Q3: Why does RSA verification ($M = S^e \pmod n$) execute significantly faster than signing ($S = M^d \pmod n$)?
**Answer:**
Because of the exponent choice! The public exponent $e$ is deliberately chosen as a small prime with very few set binary bits, typically $65537$ ($2^{16} + 1$, which has only two binary 1s: `10000000000000001`). Computing $M^{65537} \pmod n$ requires only 17 modular squarings and 1 multiplication. In contrast, the private exponent $d$ is a massive 2048-bit random integer, requiring thousands of operations. As a result, **verifying at the Gateway is ~10x faster than signing at the Auth service!**

### Q4: If RS256 is so secure, why is Ed25519 (EdDSA) becoming popular in modern systems?
**Answer:**
Ed25519 uses Elliptic Curve Cryptography (Edwards-curve Digital Signature Algorithm). It provides the same security level as RSA 3072-bit, but with a **tiny 32-byte public key** (vs. 256 bytes for RSA) and a **64-byte signature** (vs. 256 bytes for RSA), resulting in smaller HTTP headers, faster CPU performance, and immunity to side-channel timing attacks.

### Q5: If an attacker intercepts the Public Key, can they decrypt messages sent between services?
**Answer:**
No. In asymmetric cryptography:
- Data encrypted with the Public Key can **ONLY be decrypted by the matching Private Key**.
- For digital signatures (our use case), data signed with the Private Key can only be **verified** by the Public Key.
Having the Public Key grants zero ability to decrypt data or forge signatures.
