package com.ecommerce.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * =====================================================================================
 * FILE: UserResponseDto.java
 * MODULE: user-service
 * PURPOSE: Returns safe public user profile information without leaking password hashes.
 * 
 * SECURITY WISDOM:
 * Never return the User entity directly from controllers! Entities contain sensitive
 * fields (password hash, audit keys) that could be accidentally serialized to JSON.
 * =====================================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDto {

    private Long id;
    private String name;
    private String email;
    private String mobileNo;
    private String profileUrl;
    private Boolean isActive;
    private List<String> roles;
    private OffsetDateTime createdAt;

    // Populated during signup to provide immediate session tokens
    private String accessToken;
    private String refreshToken;
}
