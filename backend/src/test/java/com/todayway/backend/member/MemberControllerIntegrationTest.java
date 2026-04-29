package com.todayway.backend.member;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todayway.backend.auth.domain.RefreshToken;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MemberControllerIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("routine_commute");

    @DynamicPropertySource
    static void mysqlProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQtYmFzZTY0LXBhZGRlZC0zMmJ5dGVzLWxvbmc9PQ==");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    @Test
    void member_도메인_전체_흐름_및_회귀_가드() throws Exception {
        // (1) signup → 토큰 발급 (Step 3 흐름 재사용)
        SignupRequest signupReq = new SignupRequest("chanwoo90", "P@ssw0rd!", "찬우");
        String signupResp = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode signupNode = objectMapper.readTree(signupResp).path("data");
        String accessToken = signupNode.path("accessToken").asText();
        String signupRefreshToken = signupNode.path("refreshToken").asText();
        String memberId = signupNode.path("memberId").asText();
        String authHeader = "Bearer " + accessToken;

        // (2) GET /members/me — 본인 정보 조회 (명세 §3.1)
        mockMvc.perform(get("/api/v1/members/me")
                        .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(memberId))
                .andExpect(jsonPath("$.data.loginId").value("chanwoo90"))
                .andExpect(jsonPath("$.data.nickname").value("찬우"))
                .andExpect(jsonPath("$.data.createdAt").exists());

        // (A+ 1) 인증 헤더 누락 → 401 UNAUTHORIZED (회귀 가드 — JwtFilter chain → SecurityConfig entrypoint)
        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        // (3) PATCH nickname만 변경 → 200 (명세 §3.2)
        mockMvc.perform(patch("/api/v1/members/me")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"찬우개명\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("찬우개명"));

        // (A+ 4) PATCH body에 loginId 포함 — Jackson record unknown field 자동 무시 (loginId 수정 X 회귀 가드)
        mockMvc.perform(patch("/api/v1/members/me")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"hacker99\",\"nickname\":\"찬우3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value("chanwoo90"))   // 기존 유지
                .andExpect(jsonPath("$.data.nickname").value("찬우3"));      // nickname만 갱신

        // (4) PATCH password 변경 → 200 + 의사결정 3: signup 시 받은 refresh token 모두 폐기
        mockMvc.perform(patch("/api/v1/members/me")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"NewP@ss123!\"}"))
                .andExpect(status().isOk());

        // (A+ 3) 의사결정 3 회귀 가드 — password 변경으로 signup refresh token이 폐기됐어야 함
        String signupTokenHash = Sha256Hasher.hash(signupRefreshToken);
        RefreshToken signupSavedToken = refreshTokenRepository.findByTokenHash(signupTokenHash).orElseThrow();
        assertThat(signupSavedToken.getRevokedAt()).isNotNull();

        // 폐기된 token에 logout 호출 시 silent 204 (RFC 7009 멱등성)
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequest(signupRefreshToken))))
                .andExpect(status().isNoContent());

        // (5) DELETE /members/me → 204 소프트 삭제 (명세 §3.3)
        mockMvc.perform(delete("/api/v1/members/me")
                        .header("Authorization", authHeader))
                .andExpect(status().isNoContent());

        // (A+ 2) 탈퇴 후 동일 access token 사용 시 → 404 MEMBER_NOT_FOUND
        //   JWT 자체는 유효하지만 Member.deleted_at IS NULL 필터로 Resolver가 못 찾음
        mockMvc.perform(get("/api/v1/members/me")
                        .header("Authorization", authHeader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"));
    }
}
