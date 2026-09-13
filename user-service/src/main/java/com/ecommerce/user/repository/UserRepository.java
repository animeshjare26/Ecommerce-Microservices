package com.ecommerce.user.repository;

import com.ecommerce.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * =====================================================================================
 * FILE: UserRepository.java
 * MODULE: user-service
 * PURPOSE: Data access abstraction for User entity queries and password updates.
 * 
 * DESIGN PATTERN: Repository Pattern, Query Method Pattern.
 * 
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: Why is `@Modifying` required above `@Query` when executing an UPDATE or DELETE?
 * A1: By default, Spring Data assumes all `@Query` annotations execute SELECT statements 
 *     and calls `Query.getResultList()` or `Query.getSingleResult()`. The `@Modifying` annotation 
 *     tells Spring to call `Query.executeUpdate()` instead.
 * 
 * Q2: What is the risk of executing `@Modifying` queries without `clearAutomatically = true`?
 * A2: Stale First-Level Cache (Persistence Context)! A direct JPQL update mutates rows in the 
 *     database directly, bypassing the Hibernate entity session cache. If you fetch that entity 
 *     again in the same transaction, Hibernate returns the stale in-memory cached object unless 
 *     the persistence context is cleared!
 * =====================================================================================
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCaseAndIsActiveTrue(String email);

    boolean existsByEmailIgnoreCaseAndIsActiveTrue(String email);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.password = :password WHERE u.email = :email AND u.isActive = true")
    int updatePassword(@Param("email") String email, @Param("password") String password);
}
