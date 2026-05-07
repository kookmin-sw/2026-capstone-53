package com.todayway.backend.common.jwt;

import com.todayway.backend.common.security.PermitAllPaths;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }
}
