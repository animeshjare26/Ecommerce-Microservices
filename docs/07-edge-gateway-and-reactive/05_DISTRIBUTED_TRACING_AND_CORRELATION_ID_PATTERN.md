# Deep Dive 05: Distributed Tracing & The Correlation ID Pattern

> **Module:** `07-edge-gateway-and-reactive`  
> **Target Audience:** From Beginner Intern to Principal Architect  
> **Core Concept:** Correlation Identifier Pattern, Distributed Logging, OpenTelemetry, SLF4J MDC, TraceId vs. SpanId.

> **Status:** Partially implemented — gateway correlation IDs are implemented; cross-service tracing is planned.
>
> **Related code:** `api-gateway/src/main/java/com/ecommerce/gateway/filter/CorrelationIdFilter.java`
>
> **Last verified against:** Spring Boot 3.3.2 / Spring Cloud 2023.0.3

---

## 🟢 Tier 1: The Intuitive Mental Model (For Beginners & Interns)

### The Package Tracking Number Analogy

Imagine ordering a laptop online from Amazon:
- When you place the order, Amazon prints a single **Tracking Number** on the cardboard box: `TRK-98765`.
- As the box travels:
  1. The Warehouse scans: `[TRK-98765] Packed in Seattle`.
  2. The Cargo Plane scans: `[TRK-98765] Landed in Chicago`.
  3. The Delivery Van scans: `[TRK-98765] Out for delivery in New York`.
- If the box goes missing, customer support doesn't ask for the driver's name; they ask for the **Tracking Number**.
- They type `TRK-98765` into their computer, and the entire multi-city journey appears sequentially on screen!

#### In Microservices:
A single user click on **"Place Order"** triggers a chain reaction:
$$\text{Gateway} \rightarrow \text{Order Service} \rightarrow \text{Inventory Service} \rightarrow \text{Payment Service} \rightarrow \text{Notification Service}$$
- If payment fails, 5 different servers write millions of log lines.
- Without a tracking number, finding which line belongs to Alice's click is impossible.
- **The Correlation ID (`X-Correlation-Id`) is the tracking number stamped on every HTTP packet and log line!**

```
Client ───(X-Correlation-Id: c1f7)───► [ API Gateway ]
                                             │
                       Log: [c1f7] Ingress request received
                                             │
                                             ▼
                                      [ Order Service ]
                                             │
                       Log: [c1f7] Order #42 created, reserving stock...
                                             │
                                             ▼
                                    [ Inventory Service ]
                                             │
                       Log: [c1f7] Stock reserved successfully
```

---

## 🟡 Tier 2: Clearing Common Doubts & Anti-Patterns

### Doubt 1: "What is the difference between a `TraceId`, `SpanId`, and `CorrelationId`?"
- **`CorrelationId` (Our Implementation):** An application-level unique identifier (usually a UUID) passed via HTTP headers (`X-Correlation-Id`). It tracks an end-to-end user business transaction across services.
- **`TraceId` (OpenTelemetry / Zipkin):** A 128-bit hex string that uniquely identifies the entire distributed call tree.
- **`SpanId`:** A 64-bit hex string representing an **individual unit of work** within that trace (e.g. one SQL query or one external HTTP call). A single `TraceId` contains dozens of `SpanId`s organized as a parent-child directed acyclic graph (DAG).

### Doubt 2: "Why do we also return `X-Correlation-Id` back to the HTTP client?"
For **Customer Support and Client Observability**:
- When an unexpected error occurs, the frontend displays:
  *"Error: Transaction Failed (Reference ID: c1f7-4a8b)"*.
- When the customer calls support, the engineer pastes `c1f7-4a8b` into Kibana, Datadog, or Grafana Loki.
- The logs instantly reveal the exact root cause (e.g. *"Stripe API rejected card: Insufficient funds"*) in seconds!

---

## 🔴 Tier 3: Low-Level Internal Mechanics (For Senior Engineers)

### SLF4J MDC (Mapped Diagnostic Context)

How do microservices automatically attach `[c1f7-4a8b]` to every log output without manually passing a variable to every `log.info()` statement?

#### 1. In Blocking Tomcat Services (`user-service`):
- SLF4J uses **`MDC.put("correlationId", id)`**, which stores the ID in a `ThreadLocal` map.
- In `logback.xml`:
  ```xml
  <pattern>%d{HH:mm:ss} [%thread] [%X{correlationId}] %-5level %logger - %msg%n</pattern>
  ```
- Every log statement executed on that worker thread automatically prints the correlation ID!
- At the end of the request, `MDC.clear()` prevents thread pool data pollution.

#### 2. In Reactive Netty Services (`api-gateway`):
- Because Netty threads hop asynchronously across Project Reactor operators, `ThreadLocal` MDC does NOT work!
- We pass context through Reactor's reactive context:
  `Mono.deferContextual(ctx -> ...)` or Spring Cloud Gateway's `exchange.getRequest().mutate()`.

---

## 🏆 Tier 4: Top 5 Tricky Tier-1 Interview Questions & Winning Answers

### Q1: What happens if an external client passes their own `X-Correlation-Id` header?
**Answer:**
In `CorrelationIdFilter.java`:
- If the client supplies an `X-Correlation-Id`, the Gateway **preserves and propagates it**, allowing client-side APM tools (like Datadog RUM or Sentry in the React browser) to link browser performance metrics directly to backend server traces!
- If the header is missing, the Gateway generates a fresh `UUID.randomUUID()`.

### Q2: What is "Context Propagation" over asynchronous messaging (e.g. Apache Kafka / RabbitMQ)?
**Answer:**
When `order-service` publishes an event `OrderCreatedEvent` to a Kafka topic:
- The HTTP request ends, but the business transaction continues in `inventory-service`.
- **Context Propagation:** The producer injects the Correlation ID into the **Kafka Record Header** (`record.headers().add("X-Correlation-Id", bytes)`).
- When the Kafka consumer picks up the event, it extracts the header and injects it into its local MDC, maintaining unbroken end-to-end tracing across asynchronous message brokers!

### Q3: What is "Distributed Tracing Sampling", and why is 100% trace collection avoided at high scale?
**Answer:**
At Uber or Netflix scale (billions of requests daily), recording every single trace produces petabytes of telemetry data, saturating network bandwidth and costing millions of dollars in storage.
- **Probabilistic Sampling:** Collects a random fraction (e.g. 1% of successful traces).
- **Adaptive / Tail-Based Sampling:** Buffers traces in memory and saves **100% of errors and high-latency (p99) requests**, but discards 99% of fast, boring 200 OK requests!

### Q4: What is the difference between W3C Trace Context (`traceparent`) and custom `X-Correlation-Id`?
**Answer:**
- `X-Correlation-Id` is a legacy de facto standard used for human-readable log correlation.
- **W3C Trace Context (RFC standard):** Standardizes cross-vendor distributed tracing via the `traceparent` header:
  `version-traceid-parentid-traceflags` (e.g., `00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`). Supported natively by OpenTelemetry, AWS X-Ray, Datadog, and New Relic.

### Q5: How do you prevent log injection attacks via forged Correlation IDs?
**Answer:**
If an attacker sends an `X-Correlation-Id` containing newline characters (`\n` or `\r`), they can forge fake log lines in text-based log files (**Log Injection / CRLF Injection**).
- **Mitigation:** The Gateway validates that `X-Correlation-Id` matches an alphanumeric UUID pattern (`^[a-zA-Z0-9\\-]+$`) or sanitizes newline characters before injecting into headers and log contexts!

---

## 🛠️ Tier 5: Apply It in This Repository

### Where it appears
- Gateway correlation-ID handling is implemented in `api-gateway/src/main/java/com/ecommerce/gateway/filter/CorrelationIdFilter.java`.

### Mini exercise
- Make the gateway generate a correlation ID when absent, return it in the response, and propagate it to one downstream service.

### Failure scenario
- **Symptom:** A production error spans several services but logs cannot be connected.
- **Cause:** Each service creates a new identifier or fails to propagate the inbound trace context.
- **Fix:** Preserve W3C `traceparent` where tracing is enabled and use a validated correlation ID consistently in logs.

### Key takeaway
- Correlation IDs connect logs; trace and span IDs model causality.
- Validate client-provided identifiers before logging them.
- Sampling decisions should retain errors and high-latency traces.
