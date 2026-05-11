package com.todayway.backend.common.security;

import com.todayway.backend.common.jwt.JwtAuthenticationFilter;
import com.todayway.backend.common.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String UNAUTHORIZED_BODY =
            "{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다\",\"details\":null}}";
    private static final String FORBIDDEN_BODY =
            "{\"error\":{\"code\":\"FORBIDDEN_RESOURCE\",\"message\":\"접근 권한이 없습니다\",\"details\":null}}";

    private final JwtProvider jwtProvider;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PermitAllPaths.paths()).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                        .accessDeniedHandler(jsonAccessDeniedHandler())
                );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 명세 §1.9 — CORS 정책 Bean. 위 {@code .cors(Customizer.withDefaults())} 가 본 Bean 을
     * 자동 picking. Bean 부재 시 Spring 은 default (정책 없음) → 모든 cross-origin 차단.
     *
     * <p>{@code allowCredentials=false} — JWT 는 {@code Authorization} 헤더 stateless 인증이라
     * cookie 미사용 (프론트 {@code fetchClient.js} 도 {@code credentials} 옵션 미지정 = 브라우저
     * default {@code 'same-origin'} 동작). 향후 cookie-based refresh 도입 시 본 옵션 + origin
     * wildcard 금지 동반 재검토.
     *
     * <p>{@code allowedHeaders="*"} — 프론트 자동 부착 헤더 {@code Content-Type} / {@code Authorization}
     * 모두 커버. {@code Origin} / {@code Access-Control-Request-*} 는 simple header 라 별도 등록 X.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);
        config.setMaxAge(props.maxAgeSeconds());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(UNAUTHORIZED_BODY);
        };
    }

    private AccessDeniedHandler jsonAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(FORBIDDEN_BODY);
        };
    }
}
