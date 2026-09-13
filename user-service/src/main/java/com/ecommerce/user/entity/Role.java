package com.ecommerce.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * =====================================================================================
 * FILE: Role.java
 * MODULE: user-service
 * PURPOSE: JPA Entity representing security roles (e.g. ROLE_USER, ROLE_ADMIN).
 * 
 * DESIGN PATTERN: Domain Model Pattern (JPA Entity).
 * 
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Why should Role names be unique in the database?
 * A1: Ambiguity prevention. In Spring Security, authorization decisions (`hasRole('ADMIN')`)
 *     rely on exact string matching against the authority collection. If duplicate role records
 *     existed with varying casing or IDs, permission evaluation would become non-deterministic.
 * =====================================================================================
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    public Role(String name, String description) {
        this.name = name;
        this.description = description;
        this.isActive = Boolean.TRUE;
    }
}
