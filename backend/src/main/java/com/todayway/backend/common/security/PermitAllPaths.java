package com.todayway.backend.common.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * 인증 없이 접근 가능한 endpoint 목록의 단일 source. {@link SecurityConfig} 의 {@code permitAll()}
 * 등록과 {@link com.todayway.backend.common.jwt.JwtAuthenticationFilter} 의 만료 토큰 graceful 처리
 * 모두 본 상수를 참조해 drift 차단.
 *
 * <p>{@link #PATHS} 는 Spring Security 의 {@code requestMatchers(String...)} 에 그대로 전달.
 * {@link #matches} 는 filter 가 servlet path 비교용으로 사용. context-path 가 비어있는 가정 하에
 * {@code getRequestURI()} 로 충분.
 */
public final class PermitAllPaths {

    public static final String[] PATHS = {
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/logout",
            "/api/v1/main",
            "/api/v1/map/config",
            "/actuator/health"
    };

    private static final Set<String> PATH_SET = Set.of(PATHS);

    public static boolean matches(HttpServletRequest request) {
        return PATH_SET.contains(request.getRequestURI());
    }

    private PermitAllPaths() {
    }
}
