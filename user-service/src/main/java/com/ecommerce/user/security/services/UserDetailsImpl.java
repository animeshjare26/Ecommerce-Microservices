package com.ecommerce.user.security.services;

import com.ecommerce.user.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * =====================================================================================
 * FILE: UserDetailsImpl.java
 * MODULE: user-service
 * PURPOSE: Adapter that bridges our custom JPA User entity with Spring Security's UserDetails contract.
 * 
 * DESIGN PATTERN: Adapter Pattern.
 * 
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Why not let our JPA `User` entity directly implement `UserDetails`?
 * A1: Single Responsibility Principle (SRP) & Separation of Concerns! 
 *     Mixing JPA persistence annotations with Spring Security framework contracts couples 
 *     our core database domain model to a specific security library and pollutes entity serialization.
 * =====================================================================================
 */
public class UserDetailsImpl implements UserDetails {

    @Getter
    private final Long id;

    @Getter
    private final String email;

    private final String password;

    private final Collection<? extends GrantedAuthority> authorities;

    public UserDetailsImpl(Long id, String email, String password, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.authorities = authorities;
    }

    /**
     * Factory method to construct UserDetailsImpl from our User JPA entity.
     */
    public static UserDetailsImpl build(User user) {
        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toList());

        return new UserDetailsImpl(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                authorities
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email; // Use email as unique principal username
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
