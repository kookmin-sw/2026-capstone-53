package com.todayway.backend.common.security;

import com.todayway.backend.route.RouteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 명세 §1.9 — CORS preflight 회귀 가드.
 *
 * <p>허용 origin (yml default {@code http://localhost:3000}) 의 preflight 가 200 + Allow-Origin
 * 헤더로 응답되는지 + 차단 origin 의 preflight 는 403 Invalid CORS request 응답 + Allow-Origin
 * 헤더 부재인지 검증.
 *
 * <p>{@link RouteService} 는 {@code @MockitoBean} — 다른 통합 테스트 패턴 정합 (push 자동 트리거
 * 차단 동반).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CorsConfigurationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("routine_commute");

    @DynamicPropertySource
    static void mysqlProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQtYmFzZTY0LXBhZGRlZC0zMmJ5dGVzLWxvbmc9PQ==");
        registry.add("push.scheduler.enabled", () -> "false");
    }

    @Autowired MockMvc mockMvc;
    @MockitoBean RouteService routeService;

    @Test
    void allowedOrigin_preflightReturnsAllowOriginHeader() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void disallowedOrigin_preflightReturnsForbidden() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "http://malicious.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
