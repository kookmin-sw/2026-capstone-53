package com.todayway.backend.push.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
 * <p>{@code @Size}는 마이그레이션된 컬럼 길이 ({@code endpoint VARCHAR(2048) — V2}, {@code p256dh_key/auth_key
 * VARCHAR(255)}) 와 동기. 초과 시 SQL truncation 500 회피용 1차 가드 (명세 §1.6 정합).
 *
 * <p>{@code endpoint} max 2048 (v1.1.15) — FCM (~200) / Apple Web Push (~280) / Mozilla autopush
 * (~300-400) / Microsoft WNS (~500-2048) 모두 안전 마진 안. URL 표준상 일반적 implementation
 * 한계인 2048 을 채택해 미래 provider 추가 시 silent 400 차단.
 *
 * <p>{@code @Pattern} (v1.1.32) — Web Push 표준 (RFC 8030) 은 push service endpoint 가 HTTPS 임을
 * 요구한다. 추가로 클라이언트가 실수/악의로 {@code file://}, {@code data:}, {@code javascript:},
 * {@code http://} 등을 보내면 push provider 호출 단계에서 NPE 또는 unchecked 예외가 던져져 500 으로
 * 새는 결함이 있었다. 본 검증으로 §1.6 {@code VALIDATION_ERROR} (400) 로 fail-fast.
 * 정규식은 scheme 강제 + 공백 차단 — host/path 형식은 의도적으로 느슨 (IDN / IPv6 / 미래 provider
 * 호스트명까지 silent 차단 회피).
 */
public record PushSubscribeRequest(
        @NotBlank
        @Size(max = 2048)
        @Pattern(regexp = "^https://\\S+$", message = "endpoint는 https:// URL이어야 합니다")
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
