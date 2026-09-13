package com.ecommerce.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * =====================================================================================
 * FILE: SignUpRequestDto.java
 * MODULE: user-service
 * PURPOSE: Captures and validates user registration payloads.
 * 
 * DESIGN PATTERN: Data Transfer Object (DTO) Pattern.
 * 
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Why do we validate requests at the DTO layer using Jakarta Bean Validation (@Valid)?
 * A1: Fail-Fast principle. Invalid, malformed, or missing inputs are rejected immediately 
 *     by the Servlet layer before invoking expensive database lookups, password hashing, 
 *     or starting database transactions.
 * =====================================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignUpRequestDto {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 50, message = "Password must be at least 6 characters long")
    private String password;

    private String mobileNo;
}
