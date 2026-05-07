package com.todayway.backend.push.domain;

/**
 * 명세 §9.1 + DB-SQL.txt {@code push_log.status} ENUM.
 *
 * <ul>
 *   <li>{@link #SENT} — Web Push 서버 응답 200/201 (정상 발송)</li>
 *   <li>{@link #EXPIRED} — 응답 410 Gone (브라우저 구독 만료, 자동 revoke 트리거)</li>
 *   <li>{@link #FAILED} — 그 외 모든 실패 (네트워크/타임아웃/4xx-5xx)</li>
 * </ul>
 */
public enum PushStatus {
    SENT,
    FAILED,
    EXPIRED
}
