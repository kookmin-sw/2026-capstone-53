package com.todayway.backend.common.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 명세 §1.9 — CORS 정책 source. {@code application.yml} {@code cors.*} 키에서 주입.
 *
 * <p>Spring Boot 3 record {@code @ConfigurationProperties} — 불변 + setter 노출 X.
 * {@code @Validated} 가 ApplicationContext 시작 시점에 fail-fast.
 *
 * <p>{@code allowedOrigins} 는 yml/환경변수에서 콤마 구분 string 으로 주입 가능
 * (Spring Boot Binder 자동 split). yml default {@code http://localhost:3000} 이라 로컬
 * dev 는 env 미설정 부팅 가능. 운영 EC2 는 {@code /etc/todayway/app.env} 의
 * {@code CORS_ALLOWED_ORIGINS} 로 운영 도메인 override (별 task B 외부 노출 시 보강).
 *
 * <p>{@code maxAgeSeconds} 는 브라우저 preflight 캐시 시간. default 1800 (30분) — RFC 6454
 * 권고와 Spring default ({@code CorsConfiguration.DEFAULT_PERMIT_METHODS}) 동등 수준.
 */
@Validated
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(
        @NotEmpty List<@NotBlank String> allowedOrigins,
        @Min(0) long maxAgeSeconds
) {
}
