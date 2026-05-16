package com.todayway.backend.push.sender;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v1.1.35 — endpoint URL 마스킹 회귀 가드. push subscription resource path 가 per-device token
 * (RFC 8030 §5) 라 그 자체가 발송 권한 자격. 운영 로그에 path 가 새지 않게 origin 만 보존.
 */
class PushSenderTest {

    @Test
    void FCM_endpoint_origin만_보존_path_절단() {
        assertThat(PushSender.maskEndpoint("https://fcm.googleapis.com/fcm/send/abc123secret"))
                .isEqualTo("https://fcm.googleapis.com");
    }

    @Test
    void Mozilla_autopush_endpoint_origin만_보존() {
        assertThat(PushSender.maskEndpoint(
                "https://updates.push.services.mozilla.com/wpush/v2/gAAAAABf..."))
                .isEqualTo("https://updates.push.services.mozilla.com");
    }

    @Test
    void 비표준_포트_origin에_포함() {
        assertThat(PushSender.maskEndpoint("https://push.example.com:8443/wpush/abc"))
                .isEqualTo("https://push.example.com:8443");
    }

    @Test
    void null_endpoint_None_표기_원본누출_차단() {
        assertThat(PushSender.maskEndpoint(null)).isEqualTo("(none)");
    }

    @Test
    void blank_endpoint_None_표기() {
        assertThat(PushSender.maskEndpoint("")).isEqualTo("(none)");
        assertThat(PushSender.maskEndpoint("   ")).isEqualTo("(none)");
    }

    @Test
    void scheme_host_누락_invalid_표기_원본누출_차단() {
        // host 추출 실패 시에도 절대 원본을 반환하지 않는다 — 보안 우선.
        assertThat(PushSender.maskEndpoint("not a url")).isEqualTo("(invalid)");
        assertThat(PushSender.maskEndpoint("/relative/only")).isEqualTo("(invalid)");
    }
}
