package com.todayway.backend.push.domain;

import com.todayway.backend.common.ulid.UlidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Web Push 구독 (사용자 1명 = 기기당 1행). 명세 §7 / V1__init.sql {@code push_subscription} 정합.
 *
 * <p>row 자체는 hard-delete 하지 않고 {@code revoked_at} 으로 활성/비활성을 구분한다 —
 * push_log FK가 ON DELETE CASCADE라 row 삭제 시 발송 이력까지 사라지기 때문.
 * 따라서 BaseEntity 미상속 ({@code updated_at} 컬럼 없음, {@code created_at} 만 PrePersist).
 *
 * <p>활성 구독 조회는 항상 {@code revoked_at IS NULL} 조건을 붙여야 한다 (Repository 메서드에 명시).
 * {@code @SQLRestriction} 사용 X — soft-delete 의미가 다름 (revoke는 사용자 의도, deleted는 삭제).
 */
@Getter
@Entity
@Table(name = "push_subscription")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_uid", nullable = false, updatable = false, unique = true,
            columnDefinition = "CHAR(26)")
    private String subscriptionUid;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "endpoint", nullable = false, unique = true, length = 500)
    private String endpoint;

    @Column(name = "p256dh_key", nullable = false, length = 255)
    private String p256dhKey;

    @Column(name = "auth_key", nullable = false, length = 255)
    private String authKey;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    private PushSubscription(Long memberId, String endpoint, String p256dhKey, String authKey, String userAgent) {
        this.memberId = memberId;
        this.endpoint = endpoint;
        this.p256dhKey = p256dhKey;
        this.authKey = authKey;
        this.userAgent = userAgent;
    }

    public static PushSubscription create(Long memberId, String endpoint, String p256dhKey, String authKey,
                                          String userAgent) {
        return new PushSubscription(memberId, endpoint, p256dhKey, authKey, userAgent);
    }

    @PrePersist
    void prePersist() {
        if (subscriptionUid == null) {
            subscriptionUid = UlidGenerator.generate();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(KST);
        }
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public boolean belongsTo(Long memberId) {
        return this.memberId.equals(memberId);
    }

    /**
     * 명세 §7.1 — 동일 endpoint 재구독 시 호출. {@code revoked_at} 을 {@code NULL} 로 되돌리고
     * 키/UA 갱신 (브라우저가 키 회전했을 가능성 반영).
     */
    public void reactivate(String p256dhKey, String authKey, String userAgent) {
        this.p256dhKey = p256dhKey;
        this.authKey = authKey;
        this.userAgent = userAgent;
        this.revokedAt = null;
    }

    /**
     * 명세 §7.2 — soft revoke. 멱등 (이미 revoked면 시각 보존).
     */
    public void revoke() {
        if (revokedAt == null) {
            revokedAt = OffsetDateTime.now(KST);
        }
    }
}
