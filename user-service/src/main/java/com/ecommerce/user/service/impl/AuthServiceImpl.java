package com.ecommerce.user.service.impl;

import com.ecommerce.user.config.JwtConfig;
import com.ecommerce.user.dto.request.*;
import com.ecommerce.user.dto.response.LoginResponseDto;
import com.ecommerce.user.dto.response.UserResponseDto;
import com.ecommerce.user.entity.RefreshToken;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.enums.TokenType;
import com.ecommerce.user.exception.DataNotFoundException;
import com.ecommerce.user.exception.UnauthorizedException;
import com.ecommerce.user.repository.RefreshTokenRepository;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.security.jwt.JwtUtils;
import com.ecommerce.user.security.services.UserDetailsImpl;
import com.ecommerce.user.service.AuthService;
import com.ecommerce.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * =====================================================================================
 * FILE: AuthServiceImpl.java
 * MODULE: user-service
 * PURPOSE: Implements core authentication business logic: signup, login, refresh token 
 *          rotation, and password recovery.
 * 
 * DESIGN PATTERN: Service Layer Implementation Pattern, Facade Pattern.
 * 
 * EXECUTION FLOW (IN & OUT OF AUTHENTICATION):
 * 1. SIGNUP:
 *    - Validates email doesn't exist -> Hashes password with BCrypt -> Creates User with ROLE_USER.
 *    - Generates Access Token (15 min) + Refresh Token (7 days) with unique UUID JTI.
 *    - Saves Refresh Token to whitelist table in PostgreSQL -> Returns UserResponseDto with tokens.
 * 
 * 2. LOGIN:
 *    - Calls `authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password))`.
 *    - DaoAuthenticationProvider queries DB via UserDetailsService and verifies BCrypt hash.
 *    - If password matches: creates new Access Token and new Refresh Token.
 *    - Saves Refresh Token record in DB -> Returns LoginResponseDto.
 * 
 * 3. REFRESH TOKEN ROTATION (CRITICAL SECURITY MECHANISM):
 *    - Validates refresh token signature via JwtUtils.
 *    - Extracts token JTI claim and looks it up in `refresh_tokens` table where `is_revoked = false`.
 *    - If already revoked: REPLAY ATTACK! Immediately throws UnauthorizedException.
 *    - Marks current refresh token as REVOKED (`isRevoked = true`).
 *    - Issues a BRAND NEW Access Token and a BRAND NEW Refresh Token.
 *    - Saves new Refresh Token to DB -> Returns rotated tokens to user.
 * =====================================================================================
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    // Spring's authentication coordinator for checking credentials
    private final AuthenticationManager authenticationManager;

    // Cryptographic token helper
    private final JwtUtils jwtUtils;

    // Configuration bean for token expiration times
    private final JwtConfig jwtConfig;

    // Database access for users
    private final UserRepository userRepository;

    // Database access for refresh tokens
    private final RefreshTokenRepository refreshTokenRepository;

    // User domain service for creating user records
    private final UserService userService;

    // BCrypt password hashing engine
    private final PasswordEncoder passwordEncoder;

    // External URL for password reset links (read from application.yml)
    @Value("${app.forgot-password-web-url:http://localhost:3000/reset-password?token=}")
    private String forgotPasswordWebUrl;

    /**
     * Registers a new user, assigns default ROLE_USER, and returns active session tokens.
     */
    @Override
    @Transactional
    public UserResponseDto signUp(SignUpRequestDto signUpRequestDto) {
        log.info("Processing user signup for email: {}", signUpRequestDto.getEmail());

        // Step 1: Create user record in PostgreSQL (validates uniqueness, hashes password, assigns role)
        UserResponseDto userResponse = userService.createUser(signUpRequestDto);

        // Step 2: Generate unique UUIDs (JTI - JWT ID) for each token to enable individual token tracking
        UUID accessJti = UUID.randomUUID();
        UUID refreshJti = UUID.randomUUID();

        // Step 3: Create the Access Token (short-lived, contains user email, UUID, and assigned roles)
        String accessToken = jwtUtils.generateToken(
                TokenType.ACCESS_TOKEN,
                userResponse.getEmail(),
                Map.of("id", accessJti.toString(), "roles", userResponse.getRoles())
        );

        // Step 4: Create the Refresh Token (longer-lived, used only to get new access tokens)
        String refreshToken = jwtUtils.generateToken(
                TokenType.REFRESH_TOKEN,
                userResponse.getEmail(),
                Map.of("id", refreshJti.toString())
        );

        // Step 5: Save the Refresh Token record into PostgreSQL whitelist table
        long refreshExpMinutes = jwtConfig.getTokenConfigByType(TokenType.REFRESH_TOKEN).getExpiration();
        RefreshToken tokenEntity = new RefreshToken(
                refreshJti.toString(),
                userResponse.getEmail(),
                OffsetDateTime.now().plusMinutes(refreshExpMinutes)
        );
        refreshTokenRepository.save(tokenEntity);

        // Step 6: Attach tokens to response so the user is immediately logged in upon successful signup
        userResponse.setAccessToken(accessToken);
        userResponse.setRefreshToken(refreshToken);

        return userResponse;
    }

    /**
     * Authenticates user credentials via Spring Security and returns fresh tokens.
     */
    @Override
    @Transactional
    public LoginResponseDto login(LoginRequestDto loginRequestDto) {
        log.info("Authenticating user: {}", loginRequestDto.getEmail());

        // Step 1: Delegate authentication to Spring's AuthenticationManager
        // If password is wrong or user doesn't exist, this throws BadCredentialsException!
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequestDto.getEmail().toLowerCase().trim(),
                        loginRequestDto.getPassword()
                )
        );

        // Step 2: Extract principal (UserDetailsImpl) from the authenticated token
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        
        // Step 3: Set authentication into ThreadLocal SecurityContextHolder
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Step 4: Extract roles assigned to this user
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // Step 5: Generate unique JTIs for the new tokens
        UUID accessJti = UUID.randomUUID();
        UUID refreshJti = UUID.randomUUID();

        // Step 6: Sign the Access Token
        String accessToken = jwtUtils.generateToken(
                TokenType.ACCESS_TOKEN,
                userDetails.getEmail(),
                Map.of("id", accessJti.toString(), "roles", roles)
        );

        // Step 7: Sign the Refresh Token
        String refreshToken = jwtUtils.generateToken(
                TokenType.REFRESH_TOKEN,
                userDetails.getEmail(),
                Map.of("id", refreshJti.toString())
        );

        // Step 8: Persist the Refresh Token in PostgreSQL for rotation and revocation tracking
        long refreshExpMinutes = jwtConfig.getTokenConfigByType(TokenType.REFRESH_TOKEN).getExpiration();
        RefreshToken tokenEntity = new RefreshToken(
                refreshJti.toString(),
                userDetails.getEmail(),
                OffsetDateTime.now().plusMinutes(refreshExpMinutes)
        );
        refreshTokenRepository.save(tokenEntity);

        // Step 9: Fetch user name for personalized greeting in frontend
        User user = userRepository.findByEmailIgnoreCaseAndIsActiveTrue(userDetails.getEmail())
                .orElseThrow(() -> new DataNotFoundException("User not found"));

        // Step 10: Return DTO with both tokens and user profile summary
        return LoginResponseDto.builder()
                .userId(userDetails.getId())
                .email(userDetails.getEmail())
                .name(user.getName())
                .roles(roles)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .build();
    }

    /**
     * Executes Refresh Token Rotation:
     * Validates incoming refresh token, revokes it, and issues a fresh pair of tokens.
     */
    @Override
    @Transactional
    public LoginResponseDto refreshAccessToken(RefreshTokenRequestDto tokenRequestDto) {
        String tokenStr = tokenRequestDto.getRefreshToken();

        // Step 1: Validate token cryptographic signature and expiration
        if (!jwtUtils.validateToken(TokenType.REFRESH_TOKEN, tokenStr)) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        // Step 2: Extract claims from the valid refresh token
        Map<String, String> claims = jwtUtils.getClaimsFromToken(TokenType.REFRESH_TOKEN, tokenStr);
        String jti = claims.get("id");
        String email = claims.get("subject");

        // Step 3: Check PostgreSQL whitelist: is this JTI active and unrevoked?
        RefreshToken storedToken = refreshTokenRepository.findByTokenJtiAndIsRevokedFalse(jti)
                .orElseThrow(() -> new UnauthorizedException("Refresh token is revoked or already used (Replay attack detected!)"));

        // Step 4: Verify token has not expired past its database timestamp
        if (storedToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            storedToken.setIsRevoked(true);
            refreshTokenRepository.save(storedToken);
            throw new UnauthorizedException("Refresh token has expired");
        }

        // Step 5: REFRESH TOKEN ROTATION!
        // Immediately revoke the current refresh token so it can NEVER be reused!
        storedToken.setIsRevoked(true);
        refreshTokenRepository.save(storedToken);

        // Step 6: Verify user account is still active
        User user = userRepository.findByEmailIgnoreCaseAndIsActiveTrue(email)
                .orElseThrow(() -> new UnauthorizedException("User account no longer active"));

        List<String> roles = user.getRoles().stream().map(r -> r.getName()).collect(Collectors.toList());

        // Step 7: Generate a BRAND NEW Access Token
        UUID newAccessJti = UUID.randomUUID();
        String newAccessToken = jwtUtils.generateToken(
                TokenType.ACCESS_TOKEN,
                user.getEmail(),
                Map.of("id", newAccessJti.toString(), "roles", roles)
        );

        // Step 8: Generate a BRAND NEW rotated Refresh Token
        UUID newRefreshJti = UUID.randomUUID();
        String newRefreshToken = jwtUtils.generateToken(
                TokenType.REFRESH_TOKEN,
                user.getEmail(),
                Map.of("id", newRefreshJti.toString())
        );

        // Step 9: Save the new Refresh Token into the database
        long refreshExpMinutes = jwtConfig.getTokenConfigByType(TokenType.REFRESH_TOKEN).getExpiration();
        RefreshToken newTokenEntity = new RefreshToken(
                newRefreshJti.toString(),
                user.getEmail(),
                OffsetDateTime.now().plusMinutes(refreshExpMinutes)
        );
        refreshTokenRepository.save(newTokenEntity);

        // Step 10: Return the freshly rotated tokens
        return LoginResponseDto.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .roles(roles)
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .build();
    }

    /**
     * Generates a single-use password recovery token and returns a reset link.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, String> sendForgotPasswordLink(ForgotPasswordRequestDto request) {
        // Step 1: Verify user exists
        User user = userRepository.findByEmailIgnoreCaseAndIsActiveTrue(request.getEmail())
                .orElseThrow(() -> new DataNotFoundException("User with specified email not found"));

        // Step 2: Generate temporary password reset token (30 minutes expiry)
        UUID forgotPasswordJti = UUID.randomUUID();
        String resetToken = jwtUtils.generateToken(
                TokenType.FORGOT_PASSWORD,
                user.getEmail(),
                Map.of("id", forgotPasswordJti.toString())
        );

        // Step 3: Format the reset password web URL
        String resetLink = forgotPasswordWebUrl + resetToken;
        log.info("Password reset link generated for [{}]: {}", user.getEmail(), resetLink);

        // Step 4: In a real system, NotificationService sends this via email.
        // For local development, we return the instructions directly.
        return Map.of(
                "message", "Password reset link generated successfully",
                "resetLink", resetLink
        );
    }

    /**
     * Resets the user password after verifying the reset token and revokes all active refresh tokens.
     */
    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequestDto request) {
        // Step 1: Validate password reset token
        if (!jwtUtils.validateToken(TokenType.FORGOT_PASSWORD, request.getToken())) {
            throw new UnauthorizedException("Invalid or expired password reset token");
        }

        // Step 2: Extract user email from token subject
        Map<String, String> claims = jwtUtils.getClaimsFromToken(TokenType.FORGOT_PASSWORD, request.getToken());
        String email = claims.get("subject");

        // Step 3: Fetch active user record
        User user = userRepository.findByEmailIgnoreCaseAndIsActiveTrue(email)
                .orElseThrow(() -> new UnauthorizedException("User account not found or disabled"));

        // Step 4: Hash the new password using BCrypt
        String encodedPassword = passwordEncoder.encode(request.getNewPassword());
        userRepository.updatePassword(user.getEmail(), encodedPassword);

        // Step 5: SECURITY BEST PRACTICE!
        // When a user resets their password, REVOKE ALL existing refresh tokens!
        // This instantly boots out any malicious attacker who had an active session.
        refreshTokenRepository.revokeAllByUserEmail(user.getEmail());
        log.info("Password reset successfully and all active refresh tokens revoked for user: {}", user.getEmail());
    }
}
