package com.ecommerce.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * =====================================================================================
 * FILE: RefreshTokenRequestDto.java
 * MODULE: user-service
 * PURPOSE: Captures the refresh token string during token exchange.
 * =====================================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRequestDto {

    @NotBlank(message = "Refresh token cannot be blank")
    private String refreshToken;
}
