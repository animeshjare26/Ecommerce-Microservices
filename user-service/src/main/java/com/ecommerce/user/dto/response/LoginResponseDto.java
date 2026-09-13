package com.ecommerce.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * =====================================================================================
 * FILE: LoginResponseDto.java
 * MODULE: user-service
 * PURPOSE: Returns authentication tokens and user identity summary upon successful login.
 * =====================================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDto {

    private Long userId;
    private String email;
    private String name;
    private List<String> roles;
    private String accessToken;
    private String refreshToken;
    private String tokenType; // Bearer
}
