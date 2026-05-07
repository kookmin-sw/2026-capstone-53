package com.todayway.backend.map.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 명세 §4.2 — {@code GET /map/config} 정적 응답 source.
 * {@code application.yml} {@code map.*} 키에서 주입. 운영 환경에서 환경변수 override 허용.
 *
 * <p>설정값 자체가 부적합하면 {@code @Validated} 로 ApplicationContext 시작 시점에 fail —
 * 런타임 503 케이스가 도달하지 않도록 fail-fast.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "map")
public class MapConfigProperties {

    /** 클라이언트 지도 SDK 식별자. 명세 §4.2 예시 {@code "NAVER"}. */
    @NotBlank
    private String provider;

    /** 초기 줌 레벨. 일반 지도 SDK 범위 (0=세계, 20=상세). */
    @Min(0)
    @Max(20)
    private int defaultZoom;

    /** 위치 정보 부재 시 지도 초기 중심 좌표. */
    @Valid
    @NotNull
    private DefaultCenter defaultCenter;

    /** 타일 스타일 식별자 (예: {@code "basic"}). 클라이언트가 SDK 에 그대로 전달. */
    @NotBlank
    private String tileStyle;

    @Getter
    @Setter
    public static class DefaultCenter {
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        private double lat;

        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        private double lng;
    }
}
