package com.todayway.backend.push.sender;

import com.todayway.backend.push.domain.PushStatus;

/**
 * {@link PushSender#send} 결과. {@link com.todayway.backend.push.domain.PushLog} INSERT 시
 * {@code status} / {@code http_status} 컬럼에 그대로 매핑.
 *
 * @param status     SENT / FAILED / EXPIRED 분류
 * @param httpStatus 푸시 서버 응답 코드 — 네트워크 오류로 응답 자체가 없으면 {@code null}
 */
public record PushSendResult(PushStatus status, Integer httpStatus) {

    public PushSendResult {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (status == PushStatus.SENT && (httpStatus == null || httpStatus < 200 || httpStatus >= 300)) {
            throw new IllegalArgumentException("SENT requires 2xx httpStatus, got " + httpStatus);
        }
        if (status == PushStatus.EXPIRED && (httpStatus == null || httpStatus != 410)) {
            throw new IllegalArgumentException("EXPIRED requires 410 httpStatus, got " + httpStatus);
        }
    }

    public static PushSendResult sent(int httpStatus) {
        return new PushSendResult(PushStatus.SENT, httpStatus);
    }

    /** 푸시 서버 410 Gone — 브라우저 구독 만료 → 호출자가 {@link com.todayway.backend.push.domain.PushSubscription#revoke} 트리거. */
    public static PushSendResult expired() {
        return new PushSendResult(PushStatus.EXPIRED, 410);
    }

    public static PushSendResult failed(Integer httpStatus) {
        return new PushSendResult(PushStatus.FAILED, httpStatus);
    }

    public boolean isExpired() {
        return status == PushStatus.EXPIRED;
    }

    public boolean isSent() {
        return status == PushStatus.SENT;
    }
}
