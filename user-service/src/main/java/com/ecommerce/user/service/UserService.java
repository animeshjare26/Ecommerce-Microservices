package com.ecommerce.user.service;

import com.ecommerce.user.dto.request.SignUpRequestDto;
import com.ecommerce.user.dto.response.UserResponseDto;

/**
 * =====================================================================================
 * FILE: UserService.java
 * MODULE: user-service
 * PURPOSE: Service interface declaring user profile operations and registration creation.
 * 
 * DESIGN PATTERN: Service Layer Pattern (Interface-based design for loose coupling & testability).
 * =====================================================================================
 */
public interface UserService {

    UserResponseDto createUser(SignUpRequestDto request);

    UserResponseDto getUserById(Long id);

    UserResponseDto getUserByEmail(String email);

    boolean isEmailExists(String email);
}
