package com.todayway.backend.push.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 명세 §7.1 — Web Push 표준 PushSubscription JSON 그대로 받는다.
 *
 * <pre>{@code
 * {
 *   "endpoint": "https://fcm.googleapis.com/fcm/send/...",
 *   "keys": { "p256dh": "BNc...", "auth": "tBHI..." }
 * }
 * }</pre>
 *
 * <p>{@code @Size}는 V1__init.sql의 컬럼 길이 ({@code endpoint VARCHAR(500)},
 * {@code p256dh_key/auth_key VARCHAR(255)}) 와 동기. 초과 시 SQL truncation 500 회피용
 * 1차 가드 (명세 §1.6 정합).
 */
public record PushSubscribeRequest(
        @NotBlank
        @Size(max = 500)
        String endpoint,

        @NotNull
        @Valid
        Keys keys
) {
    public record Keys(
            @NotBlank
            @Size(max = 255)
            String p256dh,

            @NotBlank
            @Size(max = 255)
            String auth
    ) {
    }
}
