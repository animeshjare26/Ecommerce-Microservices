package com.ecommerce.user.service;

import com.ecommerce.user.dto.request.ForgotPasswordRequestDto;
import com.ecommerce.user.dto.request.LoginRequestDto;
import com.ecommerce.user.dto.request.RefreshTokenRequestDto;
import com.ecommerce.user.dto.request.ResetPasswordRequestDto;
import com.ecommerce.user.dto.request.SignUpRequestDto;
import com.ecommerce.user.dto.response.LoginResponseDto;
import com.ecommerce.user.dto.response.UserResponseDto;

import java.util.Map;

/**
 * =====================================================================================
 * FILE: AuthService.java
 * MODULE: user-service
 * PURPOSE: Service interface declaring authentication, signup, token rotation, and password reset.
 * 
 * DESIGN PATTERN: Service Layer Pattern.
 * =====================================================================================
 */
public interface AuthService {

    UserResponseDto signUp(SignUpRequestDto signUpRequestDto);

    LoginResponseDto login(LoginRequestDto loginRequestDto);

    LoginResponseDto refreshAccessToken(RefreshTokenRequestDto tokenRequestDto);

    Map<String, String> sendForgotPasswordLink(ForgotPasswordRequestDto request);

    void resetPassword(ResetPasswordRequestDto request);
}
