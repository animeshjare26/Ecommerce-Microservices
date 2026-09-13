package com.ecommerce.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * =====================================================================================
 * FILE: User.java
 * MODULE: user-service
 * PURPOSE: Core JPA Entity representing a user account in the system.
 * 
 * DESIGN PATTERN: Domain Model Pattern (Rich Domain Entity).
 * 
 * READING ORDER:
 * - Read PREVIOUS: Role.java
 * - Read THIS FILE: Understand user attributes and relationship mappings.
 * - Read NEXT: UserRepository.java, UserDetailsImpl.java
 * 
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Why do we use `Set<Role>` instead of `List<Role>` in a `@ManyToMany` relationship?
 * A1: Performance! When Hibernate manages a ManyToMany collection backed by a `List`, 
 *     removing an element forces Hibernate to issue a `DELETE FROM user_roles WHERE user_id = ?` 
 *     wiping out ALL join records and re-inserting the remaining elements one by one! 
 *     Using a `Set` allows Hibernate to issue a single targeted `DELETE` query for the specific role ID.
 * 
 * Q2: Why should Lombok's `@Data` or `@EqualsAndHashCode` NEVER be used on JPA Entities?
 * A2: `@Data` automatically generates `equals()` and `hashCode()` using all fields. If an entity 
 *     is added to a `HashSet` before being saved, its `id` is null. After `entityManager.persist()`, 
 *     the DB assigns an `id`, altering the entity's `hashCode()`! The entity can then become 
 *     "lost" inside the HashSet, causing severe memory leaks or duplicate entries in sets.
 * 
 * =====================================================================================
 * JPA & HIBERNATE ANNOTATIONS MASTERCLASS: HOW THEY WORK & DIFFER
 * =====================================================================================
 * 1. @Entity vs @Table:
 *    - @Entity informs Hibernate that this class maps to a relational database row managed 
 *      by the JPA EntityManager (tracks dirty checking, first-level caching, state transitions).
 *    - @Table(name = "users") specifies the exact database table name. If omitted, Hibernate 
 *      defaults to the class name ("user"), which is a reserved SQL keyword in PostgreSQL!
 * 
 * 2. @Id vs @GeneratedValue:
 *    - @Id designates the primary key field.
 *    - @GeneratedValue(strategy = GenerationType.IDENTITY) relies on PostgreSQL's native 
 *      auto-incrementing BIGSERIAL column. Other strategies: SEQUENCE (pre-allocates IDs via 
 *      Postgres sequence, optimal for batch inserts) and UUID.
 * 
 * 3. @Column Attributes:
 *    - nullable = false: Generates NOT NULL DDL constraint and validates before SQL execution.
 *    - unique = true: Creates a unique index/constraint in PostgreSQL.
 *    - length = 150: Prevents unbounded VARCHAR(255) allocations, saving database memory.
 *    - updatable = false: Tells Hibernate to exclude this column from generated SQL UPDATE statements.
 * 
 * 4. @CreationTimestamp vs @CreatedDate:
 *    - @CreationTimestamp is a Hibernate-specific annotation that sets the timestamp directly 
 *      in the JVM right before the SQL INSERT is dispatched.
 *    - @CreatedDate is a Spring Data JPA Auditing annotation that integrates with @EntityListeners 
 *      and AuditorAware to track auditing metadata.
 * 
 * 5. @ManyToMany with @JoinTable:
 *    - Creates an intermediary join table (`user_roles`) connecting `users.id` and `roles.id`.
 *    - FetchType.EAGER vs FetchType.LAZY: EAGER makes associated roles available when the entity is loaded,
 *      but does not guarantee Hibernate will use one SQL JOIN. It avoids lazy-loading issues for this
 *      authentication-oriented entity; use JOIN FETCH or @EntityGraph when a query requires a joined fetch.
 * =====================================================================================
 */
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(name = "mobile_no", length = 20)
    private String mobileNo;

    @Column(name = "profile_url", length = 500)
    private String profileUrl;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "modified_at")
    private OffsetDateTime modifiedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "modified_by")
    private Long modifiedBy;

    // ManyToMany association with Roles using Set for optimal join table mutation queries
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    public User(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.isActive = Boolean.TRUE;
    }
}
