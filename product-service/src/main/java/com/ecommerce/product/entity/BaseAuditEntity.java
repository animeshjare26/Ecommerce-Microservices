package com.ecommerce.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * =====================================================================================
 * FILE: product-service/.../entity/BaseAuditEntity.java
 * MODULE: product-service (Product Catalog & Category Microservice)
 * PURPOSE: Abstract base JPA entity providing automated auditing timestamps and author tracking.
 *
 * DESIGN PATTERN / ARCHITECTURAL CONCEPT:
 * - Layer Supertype / Mapped Superclass Pattern (`@MappedSuperclass`):
 *   Inherits common auditing columns without creating an extra table in the database schema.
 * - JPA Auditing Listener (`@EntityListeners(AuditingEntityListener.class)`):
 *   Delegates to Spring Data's entity listener to automatically populate timestamps
 *   and current user identifiers without manual setter calls.
 *
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Why use `OffsetDateTime` over `Instant` or `LocalDateTime` in JPA entities?
 * A1: - `LocalDateTime` contains no timezone offset, leading to disastrous time-drift bugs
 *       when servers and database clusters are distributed across regions (e.g. UTC vs IST).
 *     - `OffsetDateTime` maps natively to PostgreSQL's `TIMESTAMPTZ` (TIMESTAMP WITH TIME ZONE)
 *       and preserves the exact UTC offset, complying with ISO-8601 representation.
 *
 * Q2: How does `@CreatedBy` and `@LastModifiedBy` resolve the user ID?
 * A2: Spring Data JPA invokes the registered `AuditorAware<Long>` bean during Hibernate's
 *     pre-persist and pre-update lifecycle phases, extracting the caller's ID from our
 *     `UserContext` ThreadLocal (propagated downstream by API Gateway).
 * =====================================================================================
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseAuditEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;
}
