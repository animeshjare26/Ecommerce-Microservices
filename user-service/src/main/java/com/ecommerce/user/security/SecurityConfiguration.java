package com.ecommerce.user.security;

import com.ecommerce.user.security.jwt.AuthEntryPointJwt;
import com.ecommerce.user.security.jwt.AuthTokenFilter;
import com.ecommerce.user.security.services.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * =====================================================================================
 * FILE: SecurityConfiguration.java
 * MODULE: user-service
 * PURPOSE: The central security brain of user-service. Configures the Spring Security 6 
 *          filter chain, HTTP authorization rules, password encryption, and statelessness.
 * 
 * DESIGN PATTERN: Chain of Responsibility (Servlet Filter Pipeline), Strategy Pattern.
 * 
 * EXECUTION FLOW (HOW A REQUEST ENTERS AND MOVES THROUGH SECURITY):
 * 1. Client sends HTTP Request (e.g. POST /api/auth/login or GET /api/users/me).
 * 2. The servlet engine (Tomcat) intercepts the request and routes it to `DelegatingFilterProxy`.
 * 3. `DelegatingFilterProxy` delegates execution to Spring's `FilterChainProxy`.
 * 4. `FilterChainProxy` runs the request through the `SecurityFilterChain` defined in this file.
 * 5. Our custom `AuthTokenFilter` executes BEFORE `UsernamePasswordAuthenticationFilter`.
 *    - If public endpoint (/auth/**): filter lets it pass without demanding a token.
 *    - If protected endpoint (/users/me): filter parses the JWT, validates the signature, 
 *      loads user authorities, and sets the `Authentication` in `SecurityContextHolder`.
 * 6. If authentication fails or token is missing on protected endpoint, `AuthEntryPointJwt` 
 *    commences and returns a clean HTTP 401 JSON error without touching any Controller.
 * 7. If authentication passes, DispatcherServlet routes the request to your `@RestController`.
 * 
 * KEY SECURITY DECISIONS EXPLAINED:
 * -------------------------------------------------------------------------------------
 * 1. STATELESS SESSIONS (`SessionCreationPolicy.STATELESS`):
 *    No server-side HTTP sessions or JSESSIONID cookies in memory. Each request presents
 *    a self-contained JWT, allowing microservices to scale horizontally across any number
 *    of instances without sticky sessions.
 *
 * 2. CSRF DISABLED:
 *    CSRF attacks rely on browsers automatically sending session cookies with cross-site requests.
 *    Because this API uses explicit `Authorization: Bearer <token>` headers instead of cookies,
 *    browsers will not attach credentials automatically, making CSRF protection redundant.
 *
 * 3. BCRYPT PASSWORD ENCRYPTION:
 *    Uses adaptive slow hashing (BCrypt work factor 12) with salted hashes, making brute-force
 *    and rainbow-table attacks computationally infeasible.
 *
 * READING ORDER:
 * - Read PREVIOUS: application.yml (JWT secrets & expiration)
 * - Read THIS FILE: Understand how security rules are wired.
 * - Read NEXT: AuthTokenFilter.java (how tokens are inspected), JwtUtils.java
 *
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: What is "Stateless Architecture" in Spring Security and why is it essential for Microservices?
 * A1: In traditional stateful web apps, the server creates an `HttpSession` in RAM for every user 
 *     and sends back a `JSESSIONID` cookie. If you have 10 instances of user-service behind a load 
 *     balancer, Instance B would NOT know the session stored in Instance A's RAM (requiring sticky 
 *     sessions or distributed session replication). In a stateless architecture (`SessionCreationPolicy.STATELESS`), 
 *     the server stores ZERO session state in memory. Every request carries a cryptographically 
 *     self-contained JWT. Any instance of the microservice can independently verify the token 
 *     without asking any other server, achieving infinite horizontal scalability!
 * 
 * Q2: Why is CSRF disabled (`csrf.disable()`) in stateless REST APIs?
 * A2: Cross-Site Request Forgery (CSRF) exploits browsers automatically attaching ambient credentials 
 *     (session cookies) to cross-origin requests. In our stateless REST architecture, we do not use 
 *     cookies. Clients must explicitly attach the JWT in the `Authorization: Bearer <token>` header. 
 *     Browsers do not automatically attach an Authorization header to cross-site form submissions.
 *     Therefore CSRF protection is redundant in bearer-token REST APIs.
 * 
 * Q3: How does BCrypt work and why is it better than SHA-256 or MD5 for passwords?
 * A3: Fast hashing algorithms like SHA-256 or MD5 are designed for high throughput. An attacker
 *     with a modern GPU can compute billions of SHA-256 hashes per second to crack passwords. 
 *     BCrypt is intentionally SLOW (adaptive work factor). With strength 12, it computes 2^12 = 4,096 rounds 
 *     of hashing with an embedded 128-bit cryptographically secure random salt. This makes brute-force 
 *     and rainbow-table attacks computationally prohibitive.
 * =====================================================================================
 * SPRING ANNOTATIONS EXPLAINED:
 * =====================================================================================
 * 1. @Configuration vs @Component:
 *    - BOTH register beans in the Spring ApplicationContext.
 *    - CRUCIAL DIFFERENCE: @Configuration classes are enhanced via CGLIB dynamic subclasses!
 *      When one @Bean method calls another @Bean method inside a @Configuration class (e.g. 
 *      authenticationProvider() calling passwordEncoder()), Spring intercepts the call and returns 
 *      the EXISTING singleton bean from the IoC container instead of creating a second object in memory!
 *    - In contrast, a plain @Component does NOT use CGLIB proxying; direct method calls create a new Java instance.
 * 
 * 2. @Bean vs @Component:
 *    - @Component is placed at the CLASS level on code YOU own and write. Spring instantiates it.
 *    - @Bean is placed at the METHOD level inside @Configuration classes. It is used when you need to 
 *      instantiate third-party classes from external libraries (e.g. BCryptPasswordEncoder, ObjectMapper) 
 *      where you cannot edit the library's source code to add @Component.
 * 
 * 3. @EnableWebSecurity vs @EnableMethodSecurity:
 *    - @EnableWebSecurity operates at the SERVLET HTTP WEB LAYER. It configures the SecurityFilterChain, 
 *      CORS, CSRF, session policies, and URL-based matching (.requestMatchers("/auth/**").permitAll()).
 *    - @EnableMethodSecurity operates at the JAVA METHOD LAYER via Spring AOP proxies. It enables 
 *      annotations like @PreAuthorize("hasRole('ADMIN')") directly on controller/service methods, 
 *      evaluating permissions BEFORE the method body executes!
 * 
 * 4. @EnableJpaAuditing:
 *    - Activates Spring Data's AuditingEntityListener. Whenever an entity with @EntityListeners is saved, 
 *      Spring queries the AuditorAware bean (which extracts the user ID from SecurityContextHolder) and 
 *      automatically sets @CreatedBy and @LastModifiedBy without writing manual code.
 * 
 * 5. @RequiredArgsConstructor (Lombok) vs @Autowired:
 *    - Constructor injection is preferred over field injection because dependencies are explicit,
 *      immutable, and easy to supply in unit tests. The legacy field injection in AuthTokenFilter
 *      should be migrated separately for consistency; this configuration already uses constructor injection.
 *    - @RequiredArgsConstructor generates a constructor for all `final` fields at compile-time. 
 *      Spring automatically uses this single constructor to inject dependencies (Constructor Injection), 
 *      which is immutable, fail-fast, and enables easy mocking in unit tests (e.g. new Service(mockRepo)).
 * =====================================================================================
 */
// Marks this class as a Spring configuration class containing CGLIB-enhanced @Bean definitions
@Configuration
// Activates Spring Security's web security support and builds the SecurityFilterChain
@EnableWebSecurity
// Enables method-level authorization annotations like @PreAuthorize("hasRole('ADMIN')") via AOP proxies
@EnableMethodSecurity
// Enables Spring Data JPA Auditing to automatically populate @CreatedBy and @LastModifiedBy
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
// Lombok compile-time annotation generating constructor for all `final` fields (Clean Constructor Injection)
@RequiredArgsConstructor
public class SecurityConfiguration {

    // Custom implementation of UserDetailsService to load user records from our PostgreSQL database
    private final UserDetailsServiceImpl userDetailsService;

    // Custom OncePerRequestFilter that intercepts every request to extract and validate the JWT Bearer token
    private final AuthTokenFilter authTokenFilter;

    // Custom AuthenticationEntryPoint that writes a JSON error response when an unauthenticated request is rejected
    private final AuthEntryPointJwt authEntryPointJwt;

    /**
     * Configures the main SecurityFilterChain bean.
     * In Spring Security 6, we use functional lambda DSLs instead of deprecated chaining methods.
     * 
     * @param http the HttpSecurity builder object provided by Spring Security
     * @return the built SecurityFilterChain instance
     * @throws Exception if an error occurs while configuring the filter chain
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Step 1: Configure CORS (Cross-Origin Resource Sharing) using our custom source bean below
                // This allows browsers running frontend apps on different ports (e.g. localhost:3000) to call this API
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                
                // Step 2: Disable CSRF protection because this is a stateless REST API with Bearer tokens
                .csrf(AbstractHttpConfigurer::disable)
                
                // Step 3: Enforce STATELESS session management
                // Spring Security will NEVER create an HttpSession and will NEVER use an existing session
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                
                // Step 4: Register custom AuthenticationEntryPoint for handling 401 Unauthorized errors
                // If a user hits a protected endpoint without a valid token, this entrypoint generates a JSON response
                .exceptionHandling(exception -> exception.authenticationEntryPoint(authEntryPointJwt))
                
                // Step 5: Register the AuthenticationProvider that verifies passwords using BCrypt
                .authenticationProvider(authenticationProvider())
                
                // Step 6: Define URL-level authorization rules (Who is allowed to access what URL)
                .authorizeHttpRequests(auth -> auth
                        // Permit all requests to /auth/** (signup, login, refresh-token, forgot-password)
                        // These endpoints must be public so new or unauthenticated users can register and login!
                        .requestMatchers(
                                "/auth/**",
                                "/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/**"
                        ).permitAll()
                        
                        // Any other request not explicitly permitted above requires authentication!
                        // e.g. /users/me, /users/{id}
                        .anyRequest().authenticated()
                )
                
                // Step 7: Inject our custom AuthTokenFilter into the filter pipeline
                // It MUST run BEFORE UsernamePasswordAuthenticationFilter so our JWT authentication 
                // is already placed inside SecurityContextHolder before Spring checks credentials
                .addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class);

        // Build and return the configured immutable SecurityFilterChain bean
        return http.build();
    }

    /**
     * PasswordEncoder Bean using BCrypt hashing algorithm.
     * Work factor (rounds) is set to 12.
     * 
     * How it works:
     * - `passwordEncoder.encode("rawPassword")`: Generates a 60-character BCrypt string containing salt + hash.
     * - `passwordEncoder.matches("rawPassword", "hashedString")`: Hashes the raw password with the salt extracted
     *   from the hashed string and compares both hashes in constant time to prevent timing attacks.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // Strength 12 means 2^12 = 4,096 iterations. Balanced for strong security vs acceptable login latency (~100ms)
        return new BCryptPasswordEncoder(12);
    }

    /**
     * AuthenticationProvider Bean.
     * DaoAuthenticationProvider is Spring's standard implementation for username/password authentication.
     * 
     * How it works:
     * 1. Calls `userDetailsService.loadUserByUsername(email)` to fetch user details from PostgreSQL.
     * 2. Calls `passwordEncoder.matches(enteredPassword, storedPasswordHash)` to verify the user.
     * 3. If credentials match, returns a fully populated, authenticated `Authentication` object.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        // Tell the provider how to load the user by email from the database
        authProvider.setUserDetailsService(userDetailsService);
        // Tell the provider how to verify the password using BCrypt
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * AuthenticationManager Bean.
     * This is the coordinator that actually processes authentication requests.
     * AuthServiceImpl calls `authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password))`.
     * 
     * @param authConfig Spring's AuthenticationConfiguration helper
     * @return the primary AuthenticationManager instance
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * CORS (Cross-Origin Resource Sharing) Configuration Bean.
     * Browsers enforce the Same-Origin Policy. When a React frontend running on http://localhost:3000
     * makes a request to our API on http://localhost:8081, the browser sends an HTTP OPTIONS preflight request.
     * This configuration tells the browser that cross-origin requests are permitted.
     */
    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        
        // Allow requests from any origin pattern (localhost:3000, mobile apps, staging domains)
        config.setAllowedOriginPatterns(List.of("*"));
        
        // Allow standard HTTP methods
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        
        // Allow standard and custom HTTP headers (including Authorization, Content-Type, X-Correlation-Id)
        config.setAllowedHeaders(List.of("*"));
        
        // Allow credentials (such as Authorization headers) to be included in cross-origin requests
        config.setAllowCredentials(true);

        // Register this CORS configuration for all URL paths (/**)
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
