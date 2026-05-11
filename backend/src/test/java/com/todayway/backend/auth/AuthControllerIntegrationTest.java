package com.todayway.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todayway.backend.auth.domain.RefreshToken;
import com.todayway.backend.auth.dto.LoginRequest;
import com.todayway.backend.auth.dto.LogoutRequest;
import com.todayway.backend.auth.dto.SignupRequest;
import com.todayway.backend.auth.repository.RefreshTokenRepository;
import com.todayway.backend.common.util.Sha256Hasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("routine_commute");

    @DynamicPropertySource
    static void mysqlProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQtYmFzZTY0LXBhZGRlZC0zMmJ5dGVzLWxvbmc9PQ==");
        // 명세 §9.1 PushScheduler 30초 자동 트리거 차단 — 본 테스트가 schedule row 를 만들지 않더라도
        // matchIfMissing=true 라 default 활성화 시 다른 테스트가 만든 row 와 race 가능.
        registry.add("push.scheduler.enabled", () -> "false");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    @Test
    void signup_login_logout_full_flow() throws Exception {
        // (1) signup → 201, 응답에 memberId/loginId/nickname/accessToken/refreshToken
        SignupRequest signupReq = new SignupRequest("chanwoo90", "P@ssw0rd!", "찬우");
        String signupResp = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.memberId").exists())
                .andExpect(jsonPath("$.data.loginId").value("chanwoo90"))
                .andExpect(jsonPath("$.data.nickname").value("찬우"))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andReturn().getResponse().getContentAsString();
        String signupRefreshToken = objectMapper.readTree(signupResp).path("data").path("refreshToken").asText();

        // (2) signup 중복 → 409 LOGIN_ID_DUPLICATED
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LOGIN_ID_DUPLICATED"));

        // (3) login → 200, memberId + 토큰 2개
        LoginRequest loginReq = new LoginRequest("chanwoo90", "P@ssw0rd!");
        String loginResp = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").exists())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andReturn().getResponse().getContentAsString();

        JsonNode loginNode = objectMapper.readTree(loginResp).path("data");
        String refreshToken = loginNode.path("refreshToken").asText();

        // (4) login 잘못된 비밀번호 → 401 INVALID_CREDENTIALS
        LoginRequest wrongReq = new LoginRequest("chanwoo90", "WrongP@ss1!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        // (5) login 존재 안 하는 loginId → 401 INVALID_CREDENTIALS (MEMBER_NOT_FOUND 아님)
        LoginRequest unknownReq = new LoginRequest("ghost123", "P@ssw0rd!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unknownReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        // (6) logout → 204, body의 refreshToken 1개만 폐기 (명세 §2.3, 의사결정 4·5)
        LogoutRequest logoutReq = new LogoutRequest(refreshToken);
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutReq)))
                .andExpect(status().isNoContent());

        // (7) DB 검증: logout 후 해당 refresh_token.revoked_at NOT NULL + signup 토큰 비폐기
        //     (refresh 엔드포인트는 v1.2 예정 — API로 차단 검증 불가, DB 직접 검증)
        String tokenHash = Sha256Hasher.hash(refreshToken);
        RefreshToken saved = refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(saved.getRevokedAt()).isNotNull();

        // signup 시 받은 refreshToken은 logout 대상 아님 → revokedAt null 유지 (의사결정 5번 회귀 가드)
        String signupTokenHash = Sha256Hasher.hash(signupRefreshToken);
        RefreshToken signupSaved = refreshTokenRepository.findByTokenHash(signupTokenHash).orElseThrow();
        assertThat(signupSaved.getRevokedAt()).isNull();
    }

    @Test
    void 탈퇴_후_동일_loginId_재가입_가능_201() throws Exception {
        // 이슈 #31 회귀 가드 — soft delete 모델에서 409 LOGIN_ID_DUPLICATED 던지던 경로가
        // hard delete 전환 (명세 §3.3 v1.1.22) 으로 해소됨을 검증.
        // SignupRequest.loginId @Pattern ^[a-zA-Z0-9]{4,20}$ — underscore 비허용이라 영숫자 only.
        String loginId = "rejoinauth01";
        SignupRequest req = new SignupRequest(loginId, "P@ssw0rd!", "초기");
        String signupResp = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(signupResp).path("data").path("accessToken").asText();

        // 탈퇴 → member row 자체 + FK CASCADE 로 refresh_token row 까지 삭제
        mockMvc.perform(delete("/api/v1/members/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // 동일 loginId 로 재가입 → 201 (탈퇴 전엔 409 LOGIN_ID_DUPLICATED 였음)
        SignupRequest rejoinReq = new SignupRequest(loginId, "P@ssw0rd!", "재가입");
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejoinReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.loginId").value(loginId))
                .andExpect(jsonPath("$.data.nickname").value("재가입"));
    }
}
