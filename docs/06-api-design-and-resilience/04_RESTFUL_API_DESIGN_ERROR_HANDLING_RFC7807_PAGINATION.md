# Deep Dive 04: RESTful API Design, RFC 7807 Problem Details, and Keyset Pagination at Scale

> **Module:** `06-api-design-and-resilience`  
> **Target Audience:** Beginner Interns to Principal API Architects / Tier-1 Candidates  
> **Prerequisites:** HTTP Protocol, Spring MVC Controllers (Module 02), B-Tree Indexing (Module 03)  
> **Related Code in Project:** REST DTOs, `GenericResponse.java`, `GlobalExceptionHandler`, Pagination in `product-service`  
> **Last Verified Against:** Spring Boot 3.3.2 / RFC 7807 / RFC 9457  

---

## 🗺️ Visual Reading Order & Navigation
```text
[03_CIRCUIT_BREAKER_AND_BULKHEAD_WITH_RESILIENCE4J.md]
                         │
                         ▼
[04_RESTFUL_API_DESIGN_ERROR_HANDLING_RFC7807_PAGINATION.md]  ◄── YOU ARE HERE
                         │
                         ▼
[07-edge-gateway-and-reactive/README.md]
```

---

## 🟢 Tier 1: The Intuitive Mental Model

### The International Passport Analogy
Imagine traveling between 10 different countries:
- **Without Standard Error Contracts (Ad-hoc Chaos):**
  - Service A returns: `{"err": "Invalid Email", "code": -1}`.
  - Service B returns: `{"error_message": "User not found", "success": false}`.
  - Service C returns a raw HTML stack trace: `<html><body>500 Internal Server Error</body></html>`!
  - Every mobile app and frontend team must write 15 different parsing rules for every single microservice!
- **With RFC 7807 Problem Details (The Universal Passport):**
  Every microservice in the company formats errors using the standardized international JSON schema (`application/problem+json`). Every client library parses errors identically with zero confusion.

---

## 🟡 Tier 2: The RFC 7807 Problem Details Specification

In Spring Boot 3+, the Internet Engineering Task Force (IETF) **RFC 7807** standard is supported natively via the **`org.springframework.http.ProblemDetail`** class:

```json
{
  "type": "https://api.ecommerce.com/errors/resource-not-found",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "Product with ID '999' does not exist in the active catalog",
  "instance": "/api/v1/products/999",
  "timestamp": "2026-09-14T01:30:00Z",
  "correlationId": "f47ac10b-58cc-4372-a567-0e02b2c3d479"
}
```

### Implementing in Spring Boot 3:
```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("https://api.ecommerce.com/errors/not-found"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("correlationId", request.getHeader("X-Correlation-Id"));
        return problem;
    }
}
```

---

## 🔴 Tier 3: Pagination at Scale: Offset vs. Keyset (Cursor) Pagination

### 1. The Offset Pagination Performance Collapse
Most junior developers write pagination using `Pageable`:
```sql
SELECT * FROM orders ORDER BY id ASC LIMIT 10 OFFSET 1000000;
```
**Why this query takes 15 SECONDS on a large database:**
- To skip `OFFSET 1,000,000`, the PostgreSQL storage engine **must physically read all 1,000,000 rows off disk**, sort them, count 1,000,000, discard all 1,000,000 rows, and return only the next 10 rows!
- **Time Complexity:** $O(N)$ where $N = \text{offset}$. Latency grows linearly with page depth!
- **The Inconsistent Read Bug:** If a new row is inserted while the user flips between page 1 and page 2, rows shift positions, causing the user to see duplicate items or miss rows entirely!

---

### 2. Keyset (Cursor-Based) Pagination: The Infinite Scroll Standard
Instead of asking the database to *"skip 1,000,000 rows"*, we pass the **ID of the last item seen on the previous page**:

```sql
SELECT * FROM orders 
WHERE id > 1000000 
ORDER BY id ASC 
LIMIT 10;
```

**Why Keyset Pagination is 1,000x Faster:**
1. The query utilizes the B-Tree index on `id`.
2. PostgreSQL traverses the B-Tree root in **$O(\log N)$** (takes **2 milliseconds** whether the table has 10 rows or 100,000,000 rows!).
3. It jumps directly to `id = 1000000` and reads the next 10 physical records sequentially.
4. **Zero shifting / duplicate bugs:** Newly inserted rows never shift existing index positions.

```text
Offset Pagination:
[ 1M Discarded Rows Read from Disk... ] ──► [ Return 10 Rows ] (Slow: 15,000ms)

Keyset Pagination:
B-Tree Root ──► Jumps directly to Index [1,000,000] ──► [ Return 10 Rows ] (Fast: 2ms)
```

---

## 📐 RESTful URI Design Standards

| Operation | Good URI (Nouns & Plurals) | Anti-Pattern (Verbs in URI) |
| :--- | :--- | :--- |
| **List products** | `GET /api/v1/products?category=laptops` | `GET /api/v1/getProducts` |
| **Get product details** | `GET /api/v1/products/{id}` | `GET /api/v1/getProductById?id={id}` |
| **Create product** | `POST /api/v1/products` | `POST /api/v1/createNewProduct` |
| **Update product price** | `PATCH /api/v1/products/{id}` | `POST /api/v1/updateProductPrice` |
| **Delete product** | `DELETE /api/v1/products/{id}` | `POST /api/v1/deleteProduct/{id}` |
| **Sub-resources** | `GET /api/v1/users/{id}/orders` | `GET /api/v1/getOrdersForUser?userId={id}` |

---

## 🏆 Tier 4: Top 5 Tricky Tier-1 Interview Questions & Answers

### Q1: What is the semantic difference between HTTP `PUT` and HTTP `PATCH`?
**High-Scoring Answer:**
> - **`PUT` (Full Replacement):** Idempotent operation that replaces the **entire target resource**. If an entity has fields `[name, email, phone]` and the client sends `PUT /users/1` with payload `{"name": "Bob"}`, the missing `email` and `phone` fields must be overwritten with `null` (or their default values).
> - **`PATCH` (Partial Update):** Non-idempotent (or idempotent depending on payload) operation that updates **only the fields specified** in the request body, leaving all unspecified fields untouched in the database."

---

### Q2: What is the difference between URI Versioning, Header Versioning, and Query Param Versioning?
| Strategy | Example | Pros & Cons |
| :--- | :--- | :--- |
| **URI Path (Industry Standard)** | `/api/v1/products` | **Pros:** Clear, easy to route in API gateways, simple browser testing. **Cons:** Violates strict REST purism (URI identifies resource, not representation). |
| **Header (Content Negotiation)** | `Accept: application/vnd.company.v1+json` | **Pros:** Clean URIs, adheres to HATEOAS / REST purism. **Cons:** Difficult to test in browsers, complex gateway caching rules. |
| **Query Parameter** | `/api/products?version=1` | **Pros:** Easy to implement. **Cons:** Clutters query parameters with routing metadata. |

---

### Q3: When should an API return `204 No Content` vs `200 OK`?
**High-Scoring Answer:**
> - **`204 No Content`:** Returned when the server has successfully fulfilled the request, but there is **no message body** to send back (e.g. `DELETE /products/42`, or `PUT /users/42/password`). The HTTP response contains headers but zero body bytes.
> - **`200 OK`:** Returned when the server successfully processed the request and returns an entity representation in the response body (e.g. `GET /products/42`, or `PATCH /products/42` returning the updated product object)."

---

### Q4: Why is returning a bare JSON array (`[...]`) at the root of a REST API considered an anti-pattern?
**High-Scoring Answer:**
> "1. **Extensibility:** If an endpoint returns `[ {"id": 1}, {"id": 2} ]`, you cannot add pagination metadata (such as `totalElements`, `pageNumber`, `hasNext`) later without breaking all existing mobile and frontend client parsers!
> 2. Wrapping in an object envelope (`{ "data": [...], "pagination": { ... } }`) allows non-breaking schema evolution.
> 3. **Historical Security:** Older browsers had a JSON Array Vulnerability (JSON Hijacking via `Array.prototype` overriding); returning a top-level JSON object `{}` neutralizes this exploit."

---

### Q5: How do you implement Keyset Pagination in Spring Data JPA?
**High-Scoring Answer:**
> "By defining a repository query using an ID or timestamp cursor:
> ```java
> public interface ProductRepository extends JpaRepository<Product, Long> {
>     @Query("SELECT p FROM Product p WHERE p.id > :cursor ORDER BY p.id ASC")
>     List<Product> findNextPage(@Param("cursor") Long cursor, Pageable pageable);
> }
> ```
> The client passes the `cursor` parameter (e.g. `GET /products?cursor=150&limit=20`). The repository fetches 20 items using a fast B-Tree index range scan, and the server returns the last item's ID as the `nextCursor` for the subsequent request."
