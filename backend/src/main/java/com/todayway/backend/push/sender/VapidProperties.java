package com.todayway.backend.push.sender;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * VAPID 키페어 설정 ({@code application.yml} {@code vapid.*}).
 *
 * <p>{@link #publicKey} / {@link #privateKey} 는 {@code @NotBlank} 미부착 — {@code OdsayProperties}
 * 패턴과 동일하게 다른 도메인(member/schedule/route) 작업자가 VAPID 키 없이 백엔드를 부팅할 수
 * 있어야 한다. 비어있는 채 PushSender 가 send 호출 시 {@link IllegalStateException} 으로 발견되어
 * push 흐름만 graceful 차단된다.
 *
 * <p>{@link #subject} 는 RFC 8292 §2.1 권장 ({@code mailto:} 또는 https URL). 운영자가
 * 잘못 설정 시 푸시 서버가 401 반환 가능하므로 {@code @NotBlank} 강제.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "vapid")
public class VapidProperties {
    private String publicKey;
    private String privateKey;

    @NotBlank
    private String subject;

    public boolean isConfigured() {
        return publicKey != null && !publicKey.isBlank()
                && privateKey != null && !privateKey.isBlank();
    }
}
