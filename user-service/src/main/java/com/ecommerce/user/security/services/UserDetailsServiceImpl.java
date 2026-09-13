package com.ecommerce.user.security.services;

import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * =====================================================================================
 * FILE: UserDetailsServiceImpl.java
 * MODULE: user-service
 * PURPOSE: Implements Spring Security's UserDetailsService to load user records from PostgreSQL.
 * 
 * DESIGN PATTERN: Service Layer Pattern / Strategy Pattern.
 * 
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Why is `@Transactional(readOnly = true)` critical on `loadUserByUsername`?
 * A1: Performance & Lazy Loading! If user roles are lazily loaded (FetchType.LAZY), accessing 
 *     them outside a transactional context throws `LazyInitializationException` ("no Session"). 
 *     `readOnly = true` also signals Hibernate to skip dirty-checking snapshots, conserving CPU & memory.
 * 
 * Q2: Why annotate UserDetailsServiceImpl with `@Primary`?
 * A2: Spring Boot's UserDetailsServiceAutoConfiguration may attempt to provide a default fallback 
 *     in-memory user details manager. Marking our custom implementation with `@Primary` guarantees 
 *     Spring selects our database-backed bean unambiguously across all contexts.
 * =====================================================================================
 */
@Service
@Primary
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCaseAndIsActiveTrue(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        return UserDetailsImpl.build(user);
    }
}
