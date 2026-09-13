package com.ecommerce.user.service.impl;

import com.ecommerce.user.dto.request.SignUpRequestDto;
import com.ecommerce.user.dto.response.UserResponseDto;
import com.ecommerce.user.entity.Role;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.enums.RoleName;
import com.ecommerce.user.exception.DataNotFoundException;
import com.ecommerce.user.exception.EntityExistsException;
import com.ecommerce.user.repository.RoleRepository;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * =====================================================================================
 * FILE: UserServiceImpl.java
 * MODULE: user-service
 * PURPOSE: Implements UserService business logic, password encryption, and JPA persistence.
 * 
 * DESIGN PATTERN: Service Layer Implementation Pattern.
 * 
 * READING ORDER:
 * - Read PREVIOUS: UserService.java, UserRepository.java
 * - Read THIS FILE: Understand user persistence and role assignment.
 * - Read NEXT: AuthServiceImpl.java, UserController.java
 * 
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: What happens if an exception is thrown inside a method annotated with `@Transactional`?
 * A1: By default, Spring's TransactionInterceptor marks the transaction for ROLLBACK only 
 *     for unchecked exceptions (subclasses of `RuntimeException` and `Error`). Checked exceptions 
 *     (`Exception`) commit by default unless explicitly configured via `@Transactional(rollbackFor = Exception.class)`.
 * 
 * =====================================================================================
 * SERVICE & TRANSACTION ANNOTATIONS MASTERCLASS:
 * =====================================================================================
 * 1. @Service vs @Component:
 *    - @Service is a specialized @Component. While functionally identical in bean creation, 
 *      @Service declares that this bean contains domain business logic. It also enables 
 *      architectural pointcuts in Spring AOP (e.g. logging all calls within com..service.*).
 * 
 * 2. @Transactional (Write) vs @Transactional(readOnly = true):
 *    - @Transactional (Default): Starts a database transaction. Hibernate takes memory snapshots 
 *      of all queried entities for dirty-checking. When the method completes, any modified fields 
 *      are automatically written via SQL UPDATE statements, and the transaction commits.
 *    - @Transactional(readOnly = true): Tells Hibernate this is a pure read operation. Hibernate 
 *      skips memory snapshotting and dirty-checking, saving significant CPU and heap RAM! 
 *      In production with read replicas, the JDBC driver routes readOnly queries to secondary replicas.
 * 
 * 3. Self-Invocation Pitfall with @Transactional:
 *    - If Method A (non-transactional) calls Method B (@Transactional) in the same class:
 *      this.methodB();
 *      The transaction will NOT start! Spring @Transactional relies on CGLIB dynamic proxies. 
 *      Internal `this` calls bypass the proxy interceptor entirely!
 * =====================================================================================
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponseDto createUser(SignUpRequestDto request) {
        log.info("Creating user account for email: {}", request.getEmail());

        if (userRepository.existsByEmailIgnoreCaseAndIsActiveTrue(request.getEmail())) {
            throw new EntityExistsException("User already exists with email: " + request.getEmail());
        }

        // Assign default customer role: ROLE_USER
        Role userRole = roleRepository.findByNameIgnoreCaseAndIsActiveTrue(RoleName.ROLE_USER.name())
                .orElseThrow(() -> new DataNotFoundException("Default role ROLE_USER not found in database"));

        // Instantiate User entity
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail().toLowerCase().trim());
        // BCrypt one-way cryptographic hash
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setMobileNo(request.getMobileNo());
        user.setIsActive(Boolean.TRUE);
        user.setRoles(Set.of(userRole));

        User savedUser = userRepository.save(user);
        log.info("User created successfully with ID: {}", savedUser.getId());

        return mapToUserResponseDto(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDto getUserById(Long id) {
        User user = userRepository.findById(id)
                .filter(User::getIsActive)
                .orElseThrow(() -> new DataNotFoundException("User not found with ID: " + id));

        return mapToUserResponseDto(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDto getUserByEmail(String email) {
        User user = userRepository.findByEmailIgnoreCaseAndIsActiveTrue(email)
                .orElseThrow(() -> new DataNotFoundException("User not found with email: " + email));

        return mapToUserResponseDto(user);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEmailExists(String email) {
        return userRepository.existsByEmailIgnoreCaseAndIsActiveTrue(email);
    }

    /**
     * Converts User JPA entity to safe UserResponseDto.
     */
    private UserResponseDto mapToUserResponseDto(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .mobileNo(user.getMobileNo())
                .profileUrl(user.getProfileUrl())
                .isActive(user.getIsActive())
                .roles(user.getRoles().stream().map(Role::getName).collect(Collectors.toList()))
                .createdAt(user.getCreatedAt())
                .build();
    }
}
