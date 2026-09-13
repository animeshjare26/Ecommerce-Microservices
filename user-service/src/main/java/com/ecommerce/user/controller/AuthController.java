package com.ecommerce.user.controller;

import com.ecommerce.user.dto.request.*;
import com.ecommerce.user.dto.response.LoginResponseDto;
import com.ecommerce.user.dto.response.UserResponseDto;
import com.ecommerce.user.service.AuthService;
import com.ecommerce.user.service.UserService;
import com.ecommerce.user.utils.GenericResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * =====================================================================================
 * FILE: AuthController.java
 * MODULE: user-service
 * PURPOSE: Public REST API Controller exposing user registration, login, token refresh, 
 *          email lookup, and password recovery.
 * 
 * DESIGN PATTERN: Front Controller Pattern (Spring MVC @RestController), DTO Pattern.
 * 
 * EXECUTION FLOW (HOW CLIENT INTERACTS WITH AUTH CONTROLLER):
 * 1. Client sends JSON payload to `http://localhost:8081/api/auth/signup`.
 * 2. Tomcat delegates to DispatcherServlet -> RequestMappingHandlerMapping.
 * 3. HandlerAdapter invokes the matched method (e.g. `signup()`).
 * 4. Jakarta Bean Validation executes on `@Valid @RequestBody`:
 *    - Checks `@NotBlank`, `@Email`, `@Size`.
 *    - If validation fails: throws `MethodArgumentNotValidException`, caught by `GlobalExceptionHandler`.
 * 5. Controller invokes `AuthService` interface method.
 * 6. Wraps resulting DTO in `GenericResponse.success()` and returns appropriate HTTP status code.
 * 
 * READING ORDER:
 * - Read PREVIOUS: AuthServiceImpl.java, GenericResponse.java
 * - Read THIS FILE: Understand the public REST endpoints and response formatting.
 * - Read NEXT: UserController.java
 * =====================================================================================
 */
// Marks this class as a Spring REST Controller where every method returns response body serialized as JSON
@RestController
// Maps all endpoints in this controller under the /auth path (prefixed by /api in application.yml)
@RequestMapping("/auth")
// Lombok annotation generating constructor for final dependency fields (constructor injection)
@RequiredArgsConstructor
// Lombok annotation providing an SLF4J logger instance
@Slf4j
// Swagger OpenAPI annotation for grouping these endpoints under "Authentication" in Swagger UI
@Tag(name = "Authentication", description = "Endpoints for user registration, login, and token rotation")
public class AuthController {

    // Inject AuthService interface (decoupled from AuthServiceImpl implementation)
    private final AuthService authService;

    // Inject UserService interface
    private final UserService userService;

    /**
     * Endpoint: POST /api/auth/signup
     * Purpose: Registers a new customer and returns initial access and refresh tokens.
     * 
     * @param signUpRequestDto JSON request body validated with Jakarta Bean Validation
     * @return ResponseEntity with HTTP 201 Created and GenericResponse envelope
     */
    @PostMapping("/signup")
    @Operation(summary = "Register a new customer account")
    public ResponseEntity<GenericResponse<UserResponseDto>> signup(
            @Valid @RequestBody SignUpRequestDto signUpRequestDto) {
        
        // Log the incoming request at INFO level for observability
        log.info("REST request to signup: {}", signUpRequestDto.getEmail());
        
        // Delegate core business logic to AuthService
        UserResponseDto responseDto = authService.signUp(signUpRequestDto);
        
        // Return HTTP 201 Created because a new User database resource has been allocated
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GenericResponse.success(responseDto, "User registered successfully"));
    }

    /**
     * Endpoint: POST /api/auth/login
     * Purpose: Authenticates email/password credentials and issues fresh JWT tokens.
     * 
     * @param loginRequestDto JSON request body containing email and password
     * @return ResponseEntity with HTTP 200 OK and GenericResponse containing LoginResponseDto
     */
    @PostMapping("/login")
    @Operation(summary = "Authenticate user credentials and receive JWT access and refresh tokens")
    public ResponseEntity<GenericResponse<LoginResponseDto>> login(
            @Valid @RequestBody LoginRequestDto loginRequestDto) {
        
        log.info("REST request to login: {}", loginRequestDto.getEmail());
        
        // Authenticate credentials via AuthService
        LoginResponseDto loginResponse = authService.login(loginRequestDto);
        
        // Return HTTP 200 OK with the generated JWT access and refresh tokens
        return ResponseEntity.ok(GenericResponse.success(loginResponse, "Login successful"));
    }

    /**
     * Endpoint: POST /api/auth/refresh
     * Purpose: Exchanges a valid refresh token for a newly rotated access and refresh token pair.
     * 
     * @param tokenRequestDto JSON body containing the active refresh token string
     * @return ResponseEntity with HTTP 200 OK and newly rotated tokens
     */
    @PostMapping("/refresh")
    @Operation(summary = "Exchange a valid refresh token for rotated access and refresh tokens")
    public ResponseEntity<GenericResponse<LoginResponseDto>> refreshAccessToken(
            @Valid @RequestBody RefreshTokenRequestDto tokenRequestDto) {
        
        log.info("REST request to rotate refresh token");
        
        // Execute Refresh Token Rotation via AuthService
        LoginResponseDto response = authService.refreshAccessToken(tokenRequestDto);
        
        // Return HTTP 200 OK with the rotated tokens
        return ResponseEntity.ok(GenericResponse.success(response, "Token refreshed successfully"));
    }

    /**
     * Endpoint: GET /api/auth/email-exists?email=user@example.com
     * Purpose: Checks if an email is already taken before submitting a signup form.
     * 
     * @param email query parameter string to check
     * @return ResponseEntity with boolean data (true if registered, false if free)
     */
    @GetMapping("/email-exists")
    @Operation(summary = "Check if an email address is already registered")
    public ResponseEntity<GenericResponse<Boolean>> isEmailExists(
            @RequestParam("email") String email) {
        
        boolean exists = userService.isEmailExists(email);
        
        return ResponseEntity.ok(
                GenericResponse.success(exists, exists ? "Email is registered" : "Email is available")
        );
    }

    /**
     * Endpoint: POST /api/auth/forgot-password
     * Purpose: Generates a temporary reset password token for the provided email.
     * 
     * @param request JSON body with the user's registered email
     * @return ResponseEntity with reset instructions and link
     */
    @PostMapping("/forgot-password")
    @Operation(summary = "Generate a password reset link for the provided email")
    public ResponseEntity<GenericResponse<Map<String, String>>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDto request) {
        
        Map<String, String> response = authService.sendForgotPasswordLink(request);
        
        return ResponseEntity.ok(GenericResponse.success(response, "Password reset instructions generated"));
    }

    /**
     * Endpoint: POST /api/auth/reset-password
     * Purpose: Updates password using the single-use reset token and revokes all active sessions.
     * 
     * @param request JSON body with reset token and new password
     * @return ResponseEntity confirming password update
     */
    @PostMapping("/reset-password")
    @Operation(summary = "Reset account password using valid reset token")
    public ResponseEntity<GenericResponse<String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDto request) {
        
        authService.resetPassword(request);
        
        return ResponseEntity.ok(GenericResponse.success("Password updated successfully", "Password reset successful"));
    }
}
