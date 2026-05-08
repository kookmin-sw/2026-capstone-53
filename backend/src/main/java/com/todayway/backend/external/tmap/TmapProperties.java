package com.todayway.backend.external.tmap;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * TMAP 외부 API 설정. {@code application.yml}의 {@code tmap.*} 키에서 주입.
 * 명세 §6.1 v1.1.21 — WALK 구간 인도 곡선 제공자.
 *
 * <p>{@code appKey}는 빈 값 허용 — 다른 도메인 작업자가 TMAP 키 없이도 백엔드 시작 가능해야 한다
 * (ODsay/Kakao Local 패턴과 일관). 빈 값일 때 호출 시 401 → graceful fallback 으로 흡수.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "tmap")
public class TmapProperties {

    /** TMAP App Key. {@code ${TMAP_APP_KEY:}} 로 주입. 빈 값 허용 (graceful fallback). */
    private String appKey;

    /** {@code https://apis.openapi.sk.com/tmap}. */
    private String baseUrl;

    /** 호출 timeout. WALK 구간 한 번 호출이 ODsay searchPubTransPathT 와 같은 사이클에 포함되므로 짧게. */
    @Min(1)
    private int timeoutSeconds = 5;
}
