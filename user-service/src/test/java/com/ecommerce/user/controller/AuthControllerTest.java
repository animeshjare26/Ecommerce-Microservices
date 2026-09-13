package com.ecommerce.user.controller;

import com.ecommerce.user.dto.request.LoginRequestDto;
import com.ecommerce.user.dto.request.SignUpRequestDto;
import com.ecommerce.user.dto.response.LoginResponseDto;
import com.ecommerce.user.dto.response.UserResponseDto;
import com.ecommerce.user.security.jwt.AuthEntryPointJwt;
import com.ecommerce.user.security.jwt.AuthTokenFilter;
import com.ecommerce.user.security.jwt.JwtUtils;
import com.ecommerce.user.security.services.UserDetailsServiceImpl;
import com.ecommerce.user.service.AuthService;
import com.ecommerce.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * =====================================================================================
 * FILE: AuthControllerTest.java
 * MODULE: user-service
 * PURPOSE: Unit tests verifying AuthController endpoints, validation rules, and status codes.
 * 
 * DESIGN PATTERN: Web Layer Slice Testing (@WebMvcTest) with Mockito.
 * 
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Why use `@WebMvcTest` instead of `@SpringBootTest` for controller tests?
 * A1: Speed and test isolation! `@SpringBootTest` boots up the entire Spring ApplicationContext, 
 *     connecting to databases and loading all services. `@WebMvcTest` instantiates ONLY 
 *     the web layer (Controllers, Filters, Converters), executing tests in milliseconds.
 * =====================================================================================
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // Disable security filters to isolate controller unit logic
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserService userService;

    // Mock beans required to satisfy Spring context autowiring
    @MockBean
    private JwtUtils jwtUtils;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private AuthTokenFilter authTokenFilter;

    @MockBean
    private AuthEntryPointJwt authEntryPointJwt;

    @Test
    @DisplayName("POST /auth/signup - Success should return 201 Created with GenericResponse")
    void testSignUp_Success() throws Exception {
        SignUpRequestDto requestDto = SignUpRequestDto.builder()
                .name("John Doe")
                .email("john.doe@example.com")
                .password("securePassword123")
                .mobileNo("9876543210")
                .build();

        UserResponseDto responseDto = UserResponseDto.builder()
                .id(1L)
                .name("John Doe")
                .email("john.doe@example.com")
                .roles(List.of("ROLE_USER"))
                .accessToken("mock-access-token")
                .refreshToken("mock-refresh-token")
                .build();

        when(authService.signUp(any(SignUpRequestDto.class))).thenReturn(responseDto);

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.data.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.data.accessToken").value("mock-access-token"));
    }

    @Test
    @DisplayName("POST /auth/signup - Invalid input should return 400 Bad Request")
    void testSignUp_ValidationFailure() throws Exception {
        SignUpRequestDto invalidRequest = SignUpRequestDto.builder()
                .name("") // Empty name fails @NotBlank
                .email("not-an-email") // Invalid email fails @Email
                .password("123") // Password < 6 chars fails @Size
                .build();

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /auth/login - Valid credentials should return 200 OK with tokens")
    void testLogin_Success() throws Exception {
        LoginRequestDto loginRequest = LoginRequestDto.builder()
                .email("john.doe@example.com")
                .password("securePassword123")
                .build();

        LoginResponseDto loginResponse = LoginResponseDto.builder()
                .userId(1L)
                .email("john.doe@example.com")
                .name("John Doe")
                .roles(List.of("ROLE_USER"))
                .accessToken("mock-jwt-access-token")
                .refreshToken("mock-jwt-refresh-token")
                .tokenType("Bearer")
                .build();

        when(authService.login(any(LoginRequestDto.class))).thenReturn(loginResponse);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("mock-jwt-access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("GET /auth/email-exists - Should check email availability")
    void testIsEmailExists() throws Exception {
        when(userService.isEmailExists("existing@example.com")).thenReturn(true);

        mockMvc.perform(get("/auth/email-exists")
                        .param("email", "existing@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }
}
