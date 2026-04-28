package com.todayway.backend.auth.repository;

import com.todayway.backend.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :revokedAt " +
           "WHERE rt.memberId = :memberId AND rt.revokedAt IS NULL")
    int revokeAllActiveByMemberId(@Param("memberId") Long memberId,
                                  @Param("revokedAt") OffsetDateTime revokedAt);
}
