package com.ecommerce.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * =====================================================================================
 * FILE: DiscoveryServerApplication.java
 * MODULE: discovery-server (Netflix Eureka Service Registry)
 * PURPOSE: Bootstrap entry point for the Netflix Eureka Service Registry.
 *
 * DESIGN PATTERN / SPRING MECHANISM:
 * - Service Registry & Discovery Pattern.
 * - `@EnableEurekaServer`: Activates Spring Cloud's `EurekaServerMarkerConfiguration` and
 *   registers Jersey-based REST endpoints (`/eureka/*`) along with the Thymeleaf web dashboard.
 *
 * EXECUTION FLOW POSITION:
 * - Step 1 in Microservices Ecosystem: Must be running before all other services boot.
 * - Port: 8761
 * - Web Dashboard URL: http://localhost:8761
 *
 * READING ORDER:
 * - Read PREVIOUS: resources/application.yml
 * - Read THIS FILE: Understand how `@EnableEurekaServer` configures the registry.
 * - Read NEXT: DISCOVERY_SERVER_MASTER_GUIDE.md
 *
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Senior Question: What does `@EnableEurekaServer` do under the hood?
 * A1: It imports `EurekaServerMarkerConfiguration`, which creates a marker bean called `Marker`.
 *     Spring Boot's auto-configuration class `EurekaServerAutoConfiguration` has a condition
 *     `@ConditionalOnBean(EurekaServerMarkerConfiguration.Marker.class)`. When the marker is
 *     present, Spring registers the `PeerAwareInstanceRegistry`, the replication controllers,
 *     the eviction task, and the embedded Jersey servlet container managing `/eureka/apps/*`.
 *
 * Q2: Intermediate Question: How does Eureka handle instance heartbeat and health status?
 * A2: Every client sends a heartbeat every 30 seconds (`eureka.instance.lease-renewal-interval-in-seconds`).
 *     If the registry receives no heartbeat for 90 seconds (`lease-expiration-duration-in-seconds`),
 *     the instance is flagged DOWN and evicted. The heartbeat payload contains metadata, IP, port,
 *     and status (UP, DOWN, STARTING, OUT_OF_SERVICE).
 *
 * Q3: Beginner Intern Question: How can an administrator manually take a service out of rotation
 *     without shutting down the JVM process (e.g. for warm maintenance)?
 * A3: Eureka supports the `OUT_OF_SERVICE` state! Sending a `PUT /eureka/apps/{appId}/{instanceId}/status?value=OUT_OF_SERVICE`
 *     causes Eureka to inform all API Gateways and Ribbon/LoadBalancers to stop sending new traffic
 *     to that instance, while allowing active in-flight requests on that JVM to finish gracefully!
 * =====================================================================================
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
        System.out.println("Eureka Discovery Server Started !");
    }
}
