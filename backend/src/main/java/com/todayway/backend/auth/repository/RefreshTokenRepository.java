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

    /**
     * 멤버의 활성 refresh token 일괄 폐기.
     * 사용처: password 변경 시 (의사결정 3), 회원 탈퇴 시 (의사결정 4).
     * 미래: logout-all 엔드포인트 (E-3-4).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now WHERE rt.memberId = :memberId AND rt.revokedAt IS NULL")
    int revokeAllActiveByMemberId(@Param("memberId") Long memberId, @Param("now") OffsetDateTime now);
}
