package com.todayway.backend.common.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * 인증 없이 접근 가능한 endpoint 목록의 단일 source. {@link SecurityConfig} 의 {@code permitAll()}
 * 등록과 {@link com.todayway.backend.common.jwt.JwtAuthenticationFilter} 의 만료 토큰 graceful 처리
 * 모두 본 상수를 참조해 drift 차단.
 *
 * <p>가정: {@code server.servlet.context-path} 비어있음. context-path 도입 시 SecurityConfig 의
 * {@code requestMatchers} 와 본 {@link #matches} 의 비교 base 가 어긋나므로 둘 다 (또는
 * {@code HandlerMappingIntrospector} 로) 갱신해야 한다.
 */
public final class PermitAllPaths {

    /**
     * 내부 immutable Set — 외부 mutation 차단 + matches 의 O(1) lookup.
     */
    private static final Set<String> PATH_SET = Set.of(
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/logout",
            "/api/v1/main",
            "/api/v1/map/config",
            "/actuator/health"
    );

    /**
     * Spring Security 의 {@code requestMatchers(String...)} 시그니처 정합용. 매번 defensive copy
     * 반환 — 호출자가 mutate 해도 source 보호.
     */
    public static String[] paths() {
        return PATH_SET.toArray(String[]::new);
    }

    public static boolean matches(HttpServletRequest request) {
        return PATH_SET.contains(request.getRequestURI());
    }

    private PermitAllPaths() {
    }
}
