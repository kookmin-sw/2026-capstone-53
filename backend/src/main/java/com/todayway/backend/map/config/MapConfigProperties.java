package com.todayway.backend.map.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 명세 §4.2 — {@code GET /map/config} 정적 응답 source.
 * {@code application.yml} {@code map.*} 키에서 주입. 운영 환경에서 환경변수 override 허용.
 *
 * <p>Spring Boot 3 record {@code @ConfigurationProperties} — 불변 + setter 노출 X.
 * {@code @Validated} 가 ApplicationContext 시작 시점에 fail-fast.
 */
@Validated
@ConfigurationProperties(prefix = "map")
public record MapConfigProperties(
        @NotBlank String provider,
        @Min(0) @Max(20) int defaultZoom,
        @Valid @NotNull DefaultCenter defaultCenter,
        @NotBlank String tileStyle
) {

    public record DefaultCenter(
            @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @DecimalMin("-180.0") @DecimalMax("180.0") double lng
    ) {
    }
}
