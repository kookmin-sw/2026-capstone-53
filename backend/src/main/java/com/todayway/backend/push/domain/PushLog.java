package com.todayway.backend.push.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 푸시 발송 이력 (append-only). 명세 §9.1 / V1__init.sql {@code push_log}.
 *
 * <p>변경 메서드가 없는 immutable record-like entity. 발송 직후 한 번 INSERT 되고
 * 이후 어떤 변경도 일어나지 않는다 — BaseEntity 미상속, {@code sent_at} 만 PrePersist.
 *
 * <p>{@code subscription_id} FK CASCADE — 구독 row가 hard-delete 되면 로그도 함께 사라짐.
 * 그래서 {@link PushSubscription} 은 {@link PushSubscription#revoke()} (soft) 만 사용.
 */
@Getter
@Entity
@Table(name = "push_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushLog {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private Long subscriptionId;

    @Column(name = "schedule_id", updatable = false)
    private Long scheduleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "push_type", nullable = false, updatable = false,
            columnDefinition = "ENUM('REMINDER')")
    private PushType pushType;

    @Column(name = "payload_json", updatable = false, columnDefinition = "JSON")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false,
            columnDefinition = "ENUM('SENT','FAILED','EXPIRED')")
    private PushStatus status;

    @Column(name = "http_status", updatable = false)
    private Integer httpStatus;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private OffsetDateTime sentAt;

    private PushLog(Long subscriptionId, Long scheduleId, PushType pushType,
                    PushStatus status, Integer httpStatus, String payloadJson) {
        this.subscriptionId = subscriptionId;
        this.scheduleId = scheduleId;
        this.pushType = pushType;
        this.status = status;
        this.httpStatus = httpStatus;
        this.payloadJson = payloadJson;
    }

    public static PushLog record(Long subscriptionId, Long scheduleId, PushType pushType,
                                 PushStatus status, Integer httpStatus, String payloadJson) {
        return new PushLog(subscriptionId, scheduleId, pushType, status, httpStatus, payloadJson);
    }

    @PrePersist
    void prePersist() {
        if (sentAt == null) {
            sentAt = OffsetDateTime.now(KST);
        }
    }
}
