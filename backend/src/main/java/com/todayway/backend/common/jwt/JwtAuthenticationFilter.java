package com.todayway.backend.common.jwt;

import com.todayway.backend.common.security.PermitAllPaths;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String EXPIRED_BODY =
            "{\"error\":{\"code\":\"TOKEN_EXPIRED\",\"message\":\"토큰이 만료되었습니다\",\"details\":null}}";

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            String memberUid = jwtProvider.parseSubject(token);
            Authentication auth = new UsernamePasswordAuthenticationToken(memberUid, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (ExpiredJwtException e) {
            SecurityContextHolder.clearContext();
            // permitAll endpoint (예: 명세 §4.1 GET /main 게스트 허용) 호출자가 만료 토큰을 보내도
            // 401 차단하지 않고 게스트 흐름으로 진행 — 명세 §4.1 "게스트 허용 (인증 시 추가 정보)" 정합.
            // 인증 필요 endpoint 는 종전대로 401 TOKEN_EXPIRED 명시 응답.
            if (PermitAllPaths.matches(request)) {
                chain.doFilter(request, response);
                return;
            }
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(EXPIRED_BODY);
            return;
        } catch (JwtException | IllegalArgumentException e) {
            // Signature mismatch / malformed / unsupported algorithm / null subject — 만료 (정상 사용자
            // 시나리오) 와 달리 공격 신호 가능성. ERROR 로 elevate 해 ELK / CloudWatch 알람 / IDS-SIEM
            // 분석 대상으로 surface. 토큰 본문은 로깅 X (보안: payload 누출 차단) — class 이름 / path /
            // remote IP 만 남김 (X-Forwarded-For 우선, 없으면 RemoteAddr). 게스트 흐름은 SecurityContext
            // 만 비우고 체인 진행 (permitAll endpoint 가 401 차단 X 로 동작하게).
            log.error("JWT 검증 실패 — 위조/형식 위반 가능성 type={} path={} remote={}",
                    e.getClass().getSimpleName(), request.getServletPath(), resolveRemoteIp(request));
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }

    /** X-Forwarded-For 가 있으면 그 첫 토큰, 없으면 RemoteAddr. ELK/SIEM 분석 시 source IP 추적용. */
    private static String resolveRemoteIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }
}
