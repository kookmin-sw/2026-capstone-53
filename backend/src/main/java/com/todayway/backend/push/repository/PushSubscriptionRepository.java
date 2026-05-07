package com.todayway.backend.push.repository;

import com.todayway.backend.push.domain.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    /** 명세 §7.1 — UPSERT 분기 (신규 INSERT vs 기존 row 재활성화). endpoint UNIQUE 제약. */
    Optional<PushSubscription> findByEndpoint(String endpoint);

    /** 명세 §7.2 — DELETE /push/subscribe/{subscriptionId} 단건 조회. */
    Optional<PushSubscription> findBySubscriptionUid(String subscriptionUid);

    /**
     * 명세 §9.1 — PushScheduler 발송 대상 조회. 활성 구독만 ({@code revoked_at IS NULL}).
     * 다중 기기 시나리오: 한 회원의 모든 활성 구독에 동일 페이로드 발송.
     */
    @Query("SELECT s FROM PushSubscription s WHERE s.memberId = :memberId AND s.revokedAt IS NULL")
    List<PushSubscription> findActiveByMemberId(@Param("memberId") Long memberId);

    /**
     * 회원 탈퇴 cascade — 활성 구독 일괄 soft-revoke. {@code clearAutomatically/flushAutomatically}
     * 활성화로 영속성 컨텍스트 일관성 보장 (cascade 메서드 공통 패턴).
     *
     * @return 영향받은 활성 구독 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PushSubscription s SET s.revokedAt = :now WHERE s.memberId = :memberId AND s.revokedAt IS NULL")
    int revokeAllByMemberId(@Param("memberId") Long memberId, @Param("now") OffsetDateTime now);
}
