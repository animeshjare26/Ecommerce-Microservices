package com.ecommerce.user.repository;

import com.ecommerce.user.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * =====================================================================================
 * FILE: RoleRepository.java
 * MODULE: user-service
 * PURPOSE: Data access interface for Role entities.
 * 
 * DESIGN PATTERN: Repository Pattern (Spring Data JPA).
 * 
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Does `@Repository` do anything functional on an interface extending `JpaRepository`?
 * A1: Technically, Spring Data creates dynamic proxy implementations for any interface extending 
 *     JpaRepository automatically. However, explicit `@Repository` enables Spring's 
 *     PersistenceExceptionTranslationPostProcessor to translate low-level vendor SQL exceptions 
 *     into Spring's DataAccessException hierarchy.
 * =====================================================================================
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByNameIgnoreCaseAndIsActiveTrue(String name);
}
