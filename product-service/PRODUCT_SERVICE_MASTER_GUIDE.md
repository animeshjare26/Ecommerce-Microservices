# Product Service (`product-service`): Master Architectural Guide & Interview Textbook

> **Port:** `8082` | **Database:** PostgreSQL (`product_db`) | **Cache:** Redis (`ecommerce:product:*`)  
> **Registration:** Netflix Eureka (`lb://product-service`) | **Edge Gateway Ingress:** `/api/v1/products/**`, `/api/v1/categories/**`

---

## 1. Architectural Role & Responsibilities

The **Product Catalog Microservice** provides a high-throughput, enterprise-grade catalog and category domain for the e-commerce platform. It is designed to handle 99.9% read-heavy traffic with sub-10ms response times while strictly adhering to cloud-native microservice design principles.

### Key Architectural Responsibilities:
1. **Catalog Domain Ownership:** Manages categories and products with SKU master data, pricing, descriptions, and active status.
2. **Elimination of N+1 Hibernate Queries:** Uses JPA `@EntityGraph(attributePaths = {"category"})` to fetch products and categories in a single SQL query (`LEFT OUTER JOIN`), preventing connection pool starvation.
3. **Two-Tier Read Performance:** Combines PostgreSQL B-Tree composite indexes with Redis read-through caching (`@Cacheable`) and automatic cache invalidation (`@CacheEvict`).
4. **Event Dampening & Stock Threshold Pattern:** Exposes `StockStatus` (`IN_STOCK`, `LOW_STOCK`, `OUT_OF_STOCK`) on list views to keep cache hit ratios high, while dynamically surfacing exact counts and urgency messages on Product Details Pages only when stock drops below threshold.
5. **Decoupled Inventory Authority:** Defers live transactional write locks, atomic decrements, and stock reservations to `inventory-service` to protect catalog read performance.
6. **Downstream Role Authorization:** Validates `X-User-Roles` (`ROLE_ADMIN`, `ROLE_SELLER`) for all state mutations while keeping search and browsing public.

---

## 2. High-Level Architecture & Request Lifecycle

```
[ HTTP Client / Browser ]
           │
           │ GET /api/v1/products/search?keyword=Titanium
           ▼
┌─────────────────────────────────────────────────────────────┐
│                   SPRING CLOUD API GATEWAY                  │
│  - CorrelationIdFilter: Injects X-Correlation-Id            │
│  - JwtAuthenticationFilter: Detects public catalog GET;     │
│    allows request without token (or propagates claims if    │
│    token present)                                           │
│  - Path Rewriter: /api/v1/products/** ──► /api/products/**  │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               │ lb://product-service (Port 8082)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                      PRODUCT SERVICE                        │
│                                                             │
│  1. RoleAuthorizationInterceptor:                           │
│     - Inspects X-User-Roles & X-User-Id                     │
│     - Permits public GET; requires ROLE_ADMIN/SELLER on     │
│       mutations                                             │
│                                                             │
│  2. ProductController / CategoryController:                 │
│     - REST API endpoints wrapping responses in              │
│       GenericResponse<T> envelope                           │
│                                                             │
│  3. ProductServiceImpl / CategoryServiceImpl:               │
│     - @Cacheable(value = "products", key = "#id")           │
│     - On Cache Hit: Returns JSON directly from Redis (<2ms) │
│     - On Cache Miss: Reads from PostgreSQL via Repositories │
│     - On Mutations: @CacheEvict purges stale cache          │
│                                                             │
│  4. ProductRepository & CategoryRepository:                 │
│     - @EntityGraph(attributePaths = {"category"})           │
│     - Single SQL query with LEFT OUTER JOIN                 │
│     - Spring Data JPA Pagination & Sorting                  │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
                     PostgreSQL (product_db)
                     Flyway V1__init_product_schema
```

---

## 3. The 3 Architectural Innovations in this Service

### 3.1 Eliminating the N+1 Query Disaster via `@EntityGraph`

In naive Spring Boot applications, querying 50 products results in **51 SQL queries** (1 for the products + 50 individual queries for each category), causing catastrophic database latency:

```java
// TRAP: Default ManyToOne generates N+1 queries!
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "category_id")
private Category category;
```

#### Our Solution:
In `ProductRepository.java`, we apply `@EntityGraph`:
```java
@EntityGraph(attributePaths = {"category"})
Page<Product> findAllByActiveTrue(Pageable pageable);
```

#### Generated SQL in Hibernate:
```sql
SELECT p.id, p.sku, p.name, p.price, p.stock_status, c.id, c.name, c.slug
FROM products p
LEFT OUTER JOIN categories c ON c.id = p.category_id
WHERE p.active = true
FETCH FIRST 20 ROWS ONLY;
```
**Result:** Exactly **1 SQL Query**, 0 N+1 overhead, sub-millisecond execution!

---

### 3.2 Event Dampening & Stock Threshold Pattern

```
                       INVENTORY SERVICE (Source of Truth)
                                       │
                      Abundant Stock (1000 ──► 500 ──► 50 units)
                                       │
                    [ NO KAFKA EVENTS SENT! Cache is 100% stable! ]
                                       │
                                       ▼ (Stock drops below threshold <= 10)
                       ⚡ FIRES: ProductLowStockEvent(sku, 9)
                                       │
                                       ▼
                         PRODUCT SERVICE (@CacheEvict)
                         - Updates StockStatus = LOW_STOCK
                         - Sets display count = 9
                         - Evicts Redis cache for that product
```

- **Browse/Search API (`GET /api/products`):** Returns `ProductSummaryResponse` exposing coarse enum `stockStatus` (`IN_STOCK`, `LOW_STOCK`, `OUT_OF_STOCK`). Hiding fluctuating raw counts prevents list view thrashing and preserves cache stability.
- **Product Details Page (`GET /api/products/{id}`):** Returns `ProductDetailResponse`. When in `LOW_STOCK`, exposes `displayStockCount` and urgency message (`"Only 4 left in stock - order soon!"`).

---

### 3.3 Zero-Trust Edge Security & Downstream RBAC

1. **Authentication:** Performed at `api-gateway` via RS256 token verification.
2. **Public Access:** `GET /api/v1/products/**` and `/api/v1/categories/**` allow public discovery without authentication.
3. **Mutations:** `POST`, `PUT`, `DELETE` require valid JWTs. Gateway injects `X-User-Id` and `X-User-Roles`.
4. **Authorization Interceptor:** `RoleAuthorizationInterceptor` verifies `ROLE_ADMIN` for categories, and `ROLE_ADMIN` or `ROLE_SELLER` for products. Sellers can only modify products matching their `sellerId`.

---

## 4. Mental Reading Order (How to Explore the Code)

```text
1. pom.xml
   └── Dependencies (Spring Data JPA, Redis, Eureka, Flyway, Validation).

2. resources/application.yml & db/migration/V1__init_product_schema.sql
   └── Database configuration, composite indexes, seed data, and Redis cache TTLs.

3. enums/StockStatus.java
   └── The three stock states: IN_STOCK, LOW_STOCK, OUT_OF_STOCK.

4. entity/Category.java & entity/Product.java
   └── Domain models, soft-delete flag, and threshold recalculation logic.

5. repository/CategoryRepository.java & repository/ProductRepository.java
   └── @EntityGraph queries and composite search methods.

6. config/RedisConfig.java
   └── RedisCacheManager, Jackson JSON serialization, and custom TTLs.

7. dto/
   └── Request validation schemas and decoupled Summary vs. Detail response projections.

8. service/CategoryService.java & service/ProductService.java (and impl/)
   └── Business logic, @Cacheable read-through, and @CacheEvict invalidation.

9. security/UserContext.java & security/RoleAuthorizationInterceptor.java
   └── Downstream header extraction and RBAC enforcement.

10. controller/ProductController.java & controller/CategoryController.java
    └── REST API endpoints and OpenAPI/Swagger documentation.
```

---

## 5. Tricky Tier-1 Interview Questions & Deep Answers

### Q1: Why should you NEVER use `@Data` on JPA entities with bidirectional relationships?
**Answer:** Lombok's `@Data` automatically generates `toString()`, `equals()`, and `hashCode()` that inspect all fields. In bidirectional relationships (e.g. `Category.products` and `Product.category`), `toString()` on `Category` invokes `Product.toString()`, which in turn invokes `Category.toString()`, resulting in an instant, unrecoverable `StackOverflowError`. Always use `@Getter` and `@Setter`, with explicit business-key equality based on immutable fields (like `sku` or `slug`).

### Q2: Why is `FetchType.EAGER` banned in production JPA architectures?
**Answer:** In JPA, `@ManyToOne` defaults to `FetchType.EAGER`. If you query 100 products using `findAll()`, Hibernate executes 1 query for products and then 100 separate queries for each product's category (N+1 query problem). Even worse, setting `FetchType.EAGER` cannot be overridden dynamically at query time. The industry best practice is to declare `FetchType.LAZY` everywhere and use `@EntityGraph` or `JOIN FETCH` only when the relationship is needed.

### Q3: Why did we use `Long` instead of `Integer` for `stockQuantity` and `lowStockThreshold`?
**Answer:** While standard retail products rarely exceed 2 billion units, enterprise inventory systems tracking component parts, raw materials, or multi-warehouse global stock can easily exceed the 32-bit signed integer cap ($2^{31}-1 \approx 2.14 \times 10^9$). Using `Long` (PostgreSQL `BIGINT`) prevents silent integer overflow bugs and aligns with database primary key identifier types.

### Q4: What is the risk of using `ddl-auto = update` in microservices, and why is Flyway superior?
**Answer:** Hibernate's `ddl-auto = update` is non-deterministic and dangerous. It can add columns or tables, but will never drop deleted columns or constraints. Under multi-instance horizontal scaling, multiple microservice instances booting simultaneously with `update` can cause race conditions and lock contention in Postgres system catalogs. Flyway uses transactional migrations (`V1__...sql`) with cryptographic checksums and distributed advisory locks, guaranteeing deterministic schema evolution across all environments.
