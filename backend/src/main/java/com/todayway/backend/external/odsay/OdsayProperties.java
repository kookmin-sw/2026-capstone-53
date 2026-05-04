package com.todayway.backend.external.odsay;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * ODsay 외부 API 설정. {@code application.yml}의 {@code odsay.*} 키에서 주입.
 * <p>{@code @Validated} + 필드별 제약으로 시작 시점에 fail-fast — 운영자가 timeoutSeconds=0
 * 같은 값으로 잘못 설정하면 모든 ODsay 호출이 즉시 timeout으로 떨어져 graceful path만 활성되는
 * silent 운영 사고 차단.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "odsay")
public class OdsayProperties {
    /**
     * ODsay API 키. {@code ${ODSAY_API_KEY:}}로 주입되어 로컬 dev 환경에선 빈 값 가능 —
     * @NotBlank를 강제하면 다른 도메인 작업자(member/schedule/push)가 ODsay 키 없이
     * 백엔드 시작 못 함. 빈 값은 ODsay 호출 시점에 401로 떨어져 §5.1/§6.1 graceful 정책으로 흡수.
     */
    private String apiKey;

    @NotBlank
    private String baseUrl;

    @Min(1)
    private int timeoutSeconds = 5;

    /** 명세 §6.1 — schedule.route_summary_json 캐시 TTL (분). 권장 10분. */
    @Min(1)
    private int cacheTtlMinutes = 10;
}
