package com.ecommerce.user.service;

import com.ecommerce.user.config.JwtConfig;
import com.ecommerce.user.dto.request.LoginRequestDto;
import com.ecommerce.user.dto.request.RefreshTokenRequestDto;
import com.ecommerce.user.dto.request.SignUpRequestDto;
import com.ecommerce.user.dto.response.LoginResponseDto;
import com.ecommerce.user.dto.response.UserResponseDto;
import com.ecommerce.user.entity.RefreshToken;
import com.ecommerce.user.entity.Role;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.enums.RoleName;
import com.ecommerce.user.enums.TokenType;
import com.ecommerce.user.exception.UnauthorizedException;
import com.ecommerce.user.repository.RefreshTokenRepository;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.security.jwt.JwtUtils;
import com.ecommerce.user.security.services.UserDetailsImpl;
import com.ecommerce.user.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * =====================================================================================
 * FILE: AuthServiceTest.java
 * MODULE: user-service
 * PURPOSE: Unit tests verifying AuthService business logic, token generation, and rotation.
 * 
 * DESIGN PATTERN: Isolated Service Unit Test with Mockito Extension.
 * =====================================================================================
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private JwtConfig jwtConfig;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthServiceImpl authService;

    private JwtConfig.TokenConfig accessConfig;
    private JwtConfig.TokenConfig refreshConfig;

    @BeforeEach
    void setUp() {
        accessConfig = new JwtConfig.TokenConfig();
        accessConfig.setSecret("dGhpc0lzQVZlcnlTZWN1cmVTZWNyZXRLZXlGb3JBY2Nlc3NUb2tlbjEyMzQ1Ng==");
        accessConfig.setExpiration(15);

        refreshConfig = new JwtConfig.TokenConfig();
        refreshConfig.setSecret("dGhpc0lzQURpZmZlcmVudFNlY3VyZVNlY3JldEtleUZvclJlZnJlc2hUb2tlbjc4OTA=");
        refreshConfig.setExpiration(10080);
    }

    @Test
    @DisplayName("signUp() - Should create user and issue access and refresh tokens")
    void testSignUp_Success() {
        SignUpRequestDto requestDto = SignUpRequestDto.builder()
                .name("Alice")
                .email("alice@example.com")
                .password("secret123")
                .build();

        UserResponseDto createdUser = UserResponseDto.builder()
                .id(10L)
                .name("Alice")
                .email("alice@example.com")
                .roles(List.of("ROLE_USER"))
                .build();

        when(userService.createUser(any(SignUpRequestDto.class))).thenReturn(createdUser);
        when(jwtConfig.getTokenConfigByType(TokenType.REFRESH_TOKEN)).thenReturn(refreshConfig);
        when(jwtUtils.generateToken(eq(TokenType.ACCESS_TOKEN), eq("alice@example.com"), any())).thenReturn("access-token-123");
        when(jwtUtils.generateToken(eq(TokenType.REFRESH_TOKEN), eq("alice@example.com"), any())).thenReturn("refresh-token-123");

        UserResponseDto result = authService.signUp(requestDto);

        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isEqualTo("access-token-123");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token-123");
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("login() - Valid credentials should authenticate and return tokens")
    void testLogin_Success() {
        LoginRequestDto loginDto = new LoginRequestDto("alice@example.com", "secret123");

        UserDetailsImpl userDetails = new UserDetailsImpl(
                10L,
                "alice@example.com",
                "encodedPassword",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);

        when(jwtConfig.getTokenConfigByType(TokenType.REFRESH_TOKEN)).thenReturn(refreshConfig);
        when(jwtUtils.generateToken(eq(TokenType.ACCESS_TOKEN), eq("alice@example.com"), any())).thenReturn("new-access-token");
        when(jwtUtils.generateToken(eq(TokenType.REFRESH_TOKEN), eq("alice@example.com"), any())).thenReturn("new-refresh-token");

        User user = new User("Alice", "alice@example.com", "encodedPassword");
        when(userRepository.findByEmailIgnoreCaseAndIsActiveTrue("alice@example.com")).thenReturn(Optional.of(user));

        LoginResponseDto response = authService.login(loginDto);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("refreshAccessToken() - Should rotate refresh token when valid")
    void testRefreshAccessToken_Success() {
        RefreshTokenRequestDto request = new RefreshTokenRequestDto("valid-refresh-token");

        when(jwtUtils.validateToken(TokenType.REFRESH_TOKEN, "valid-refresh-token")).thenReturn(true);
        when(jwtUtils.getClaimsFromToken(TokenType.REFRESH_TOKEN, "valid-refresh-token"))
                .thenReturn(Map.of("id", "jti-123", "subject", "alice@example.com"));

        RefreshToken storedToken = new RefreshToken("jti-123", "alice@example.com", OffsetDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenJtiAndIsRevokedFalse("jti-123")).thenReturn(Optional.of(storedToken));

        User user = new User("Alice", "alice@example.com", "pass");
        Role role = new Role("ROLE_USER", "Customer");
        user.setRoles(Set.of(role));
        when(userRepository.findByEmailIgnoreCaseAndIsActiveTrue("alice@example.com")).thenReturn(Optional.of(user));

        when(jwtConfig.getTokenConfigByType(TokenType.REFRESH_TOKEN)).thenReturn(refreshConfig);
        when(jwtUtils.generateToken(eq(TokenType.ACCESS_TOKEN), eq("alice@example.com"), any())).thenReturn("rotated-access-token");
        when(jwtUtils.generateToken(eq(TokenType.REFRESH_TOKEN), eq("alice@example.com"), any())).thenReturn("rotated-refresh-token");

        LoginResponseDto response = authService.refreshAccessToken(request);

        assertThat(response.getAccessToken()).isEqualTo("rotated-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("rotated-refresh-token");
        assertThat(storedToken.getIsRevoked()).isTrue(); // Verifies old token was revoked!
    }

    @Test
    @DisplayName("refreshAccessToken() - Replay attack with revoked token should throw UnauthorizedException")
    void testRefreshAccessToken_ReplayAttack_ThrowsException() {
        RefreshTokenRequestDto request = new RefreshTokenRequestDto("stolen-revoked-token");

        when(jwtUtils.validateToken(TokenType.REFRESH_TOKEN, "stolen-revoked-token")).thenReturn(true);
        when(jwtUtils.getClaimsFromToken(TokenType.REFRESH_TOKEN, "stolen-revoked-token"))
                .thenReturn(Map.of("id", "revoked-jti", "subject", "alice@example.com"));

        // Repository returns empty because isRevoked = true
        when(refreshTokenRepository.findByTokenJtiAndIsRevokedFalse("revoked-jti")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshAccessToken(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Replay attack detected");
    }
}
