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
import java.util.Objects;

/**
 * Web Push 구독 (사용자 1명 = 기기당 1행). 명세 §7 / V1__init.sql {@code push_subscription} 정합.
 *
 * <p>row 자체는 hard-delete 하지 않고 {@code revoked_at} 으로 활성/비활성을 구분한다 —
 * push_log FK 가 ON DELETE CASCADE 라 row 삭제 시 발송 이력까지 사라지기 때문.
 * 따라서 BaseEntity 미상속 ({@code updated_at} 컬럼 없음, {@code created_at} 만 PrePersist).
 *
 * <p>활성 구독 조회는 항상 {@code revoked_at IS NULL} 조건을 붙여야 한다 (Repository 메서드에 명시).
 * {@code @SQLRestriction} 사용 X — soft-delete 의미가 다름 (revoke 는 사용자 의도, deleted 는 삭제).
 *
 * <p>{@code endpoint} 와 {@code member_id} 모두 {@code updatable=false} — UPSERT 의 row 식별자
 * (endpoint UNIQUE) 와 소유자(member_id) 모두 INSERT 후 변경 X. 변경 시 silent identity drift
 * 위험이라 컬럼 레벨에서 차단.
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

    @Column(name = "endpoint", nullable = false, updatable = false, unique = true, length = 500)
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

    /**
     * 입력 검증은 호출자(DTO {@code @NotBlank} / Service)의 책임. 본 entity 는 검증 X —
     * IllegalArgumentException 으로 entity 검증을 추가하면 GlobalExceptionHandler 에 해당 핸들러가
     * 없어 silent 500 으로 떨어진다. 컬럼 제약(NOT NULL / UNIQUE / VARCHAR length) 으로 DB 레벨 가드.
     */
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
        return Objects.equals(this.memberId, memberId);
    }

    /**
     * 명세 §7.1 — 브라우저가 키 회전한 케이스 반영 (재구독 시 키/UA 갱신, {@code revoked_at} 해제).
     * 입력 검증은 호출자 책임 (DTO {@code @NotBlank}).
     */
    public void reactivate(String p256dhKey, String authKey, String userAgent) {
        this.p256dhKey = p256dhKey;
        this.authKey = authKey;
        this.userAgent = userAgent;
        this.revokedAt = null;
    }

    /**
     * 명세 §7.2 — soft revoke. 멱등 (이미 revoked 면 시각 보존).
     */
    public void revoke() {
        if (revokedAt == null) {
            revokedAt = OffsetDateTime.now(KST);
        }
    }
}
