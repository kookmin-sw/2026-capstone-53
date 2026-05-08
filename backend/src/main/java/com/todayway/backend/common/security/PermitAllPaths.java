package com.todayway.backend.common.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * 인증 없이 접근 가능한 endpoint 목록의 단일 source. {@link SecurityConfig} 의 {@code permitAll()}
 * 등록과 {@link com.todayway.backend.common.jwt.JwtAuthenticationFilter} 의 만료 토큰 graceful 처리
 * 모두 본 상수를 참조해 drift 차단.
 *
 * <p>{@link #matches}는 {@link HttpServletRequest#getServletPath()} 로 비교 — Spring MVC
 * {@code requestMatchers(String...)} 가 normalize 하는 경로 (path parameter `;jsessionid=…` 제외,
 * context-path 제외) 와 같은 base 라 silent 401 drift 차단. raw {@code getRequestURI()} 비교 시
 * `/api/v1/main;jsessionid=xxx` 같은 입력이 두 곳에서 다르게 평가될 위험이 있음.
 *
 * <p><b>운영 가정</b>:
 * <ul>
 *   <li>{@code spring.mvc.servlet.path=/} (Spring Boot 디폴트) — DispatcherServlet 매핑이 root
 *       이라 {@link HttpServletRequest#getServletPath()} 가 path 전체를 반환. 운영자가 본 키를
 *       바꾸면 servletPath 가 prefix-strip 되어 PATH_SET 매칭 실패 → permitAll endpoint 가 만료
 *       토큰 시 401 차단. 변경 시 PATH_SET 의 path 들도 같은 base 로 재정의 필요.</li>
 *   <li>회귀 가드는 통합 테스트 (Tomcat embedded — 운영 환경 동등)가 담당. 단위 mock 환경은
 *       {@code request.setServletPath(...)} 명시 set 필요 (디폴트 빈 문자열).</li>
 * </ul>
 *
 * <p>SecurityConfig 의 {@code requestMatchers} 와 본 matches 가 정확히 같은 implementation 을 쓰는
 * 것은 아니다 — Spring Security 6 의 {@code MvcRequestMatcher} 는 {@code HandlerMappingIntrospector}
 * 의 PathPattern 을 사용하는 반면 본 matches 는 servletPath 의 직접 contains. 디폴트 매핑/path-only
 * 시나리오에서는 동작 동등하지만 wildcard / variable / 인코딩 케이스에선 drift 가능. 현 PATH_SET 은
 * 모두 정적 literal path 라 drift 위험 0 — 추가 시 invariant 유지 필수.
 */
public final class PermitAllPaths {

    /** path 문자열 array — Spring Security 의 {@code requestMatchers(String...)} 시그니처 정합. */
    private static final String[] PATHS = {
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/logout",
            "/api/v1/main",
            "/api/v1/map/config",
            "/actuator/health"
    };

    /** matches 의 O(1) lookup 용. */
    private static final Set<String> PATH_SET = Set.of(PATHS);

    /** Spring Security 의 {@code requestMatchers(String...)} 시그니처 정합용 — startup 1회만 사용. */
    public static String[] paths() {
        return PATHS;
    }

    /**
     * 본 메서드는 {@link HttpServletRequest#getServletPath()} 를 비교 — context-path 와
     * path parameter (RFC 3986 §3.3 의 {@code ;name=value}) 가 자동 strip 된 path. Spring MVC
     * {@link org.springframework.web.util.pattern.PathPattern} normalize 와 같은 base 라
     * SecurityConfig 와 본 클래스의 평가 결과가 항상 일치한다.
     */
    public static boolean matches(HttpServletRequest request) {
        return PATH_SET.contains(request.getServletPath());
    }

    private PermitAllPaths() {
    }
}
