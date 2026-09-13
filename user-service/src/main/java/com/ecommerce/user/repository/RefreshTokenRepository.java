package com.ecommerce.user.repository;

import com.ecommerce.user.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * =====================================================================================
 * FILE: RefreshTokenRepository.java
 * MODULE: user-service
 * PURPOSE: Data access operations for RefreshToken whitelist validation and revocation.
 * 
 * DESIGN PATTERN: Repository Pattern.
 * =====================================================================================
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenJtiAndIsRevokedFalse(String tokenJti);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.isRevoked = true WHERE r.userEmail = :userEmail")
    void revokeAllByUserEmail(@Param("userEmail") String userEmail);
}
