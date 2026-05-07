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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
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
        // PushScheduler 자동 트리거 차단 — cascade 테스트가 schedule row 를 만들고 reminder_at 이
        // 박힌 채로 30초 윈도우에 진입하면 scheduler 가 잡아 실 RouteService 호출 위험.
        registry.add("push.scheduler.enabled", () -> "false");
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

        // (A+ 2) 탈퇴 후 동일 access token 사용 시 → 401 UNAUTHORIZED (β PR 후 명세 §3.3 v1.1.7)
        //   JWT 자체는 유효하지만 Service의 findByMemberUid가 @SQLRestriction("deleted_at IS NULL")로
        //   None 반환 → throw BusinessException(UNAUTHORIZED). Resolver는 raw memberUid 반환만, DB 호출 X.
        mockMvc.perform(get("/api/v1/members/me")
                        .header("Authorization", authHeader))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // ──────────── happy path + validation 5 케이스 (이상진 B-6/B-7 + Q1-B 정책 검증) ────────────

    @Test
    void getMe_happyPath_returns200WithMemberFields() throws Exception {
        SignupResult signup = signupNew("happypath01", "행복");
        mockMvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + signup.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(signup.memberId()))
                .andExpect(jsonPath("$.data.memberId", startsWith("mem_")))
                .andExpect(jsonPath("$.data.loginId").value("happypath01"))
                .andExpect(jsonPath("$.data.nickname").value("행복"))
                .andExpect(jsonPath("$.data.createdAt", containsString("+09:00")));
    }

    @Test
    void update_nicknameOnly_returns200AndUpdated() throws Exception {
        SignupResult signup = signupNew("nickonly01", "초기");
        mockMvc.perform(patch("/api/v1/members/me")
                        .header("Authorization", "Bearer " + signup.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"변경후\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("변경후"))
                .andExpect(jsonPath("$.data.loginId").value("nickonly01"));
    }

    @Test
    void update_passwordOnly_canLoginWithNewPassword() throws Exception {
        SignupResult signup = signupNew("pwonly01", "비번변경");

        mockMvc.perform(patch("/api/v1/members/me")
                        .header("Authorization", "Bearer " + signup.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"NewP@ss123!\"}"))
                .andExpect(status().isOk());

        // 새 비번으로 login → 200 (이상진 B-7 실효 검증)
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"pwonly01\",\"password\":\"NewP@ss123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists());

        // 옛 비번으로 login → 401 (회귀 가드)
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"pwonly01\",\"password\":\"P@ssw0rd!\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void update_emptyBody_returns400ValidationError() throws Exception {
        SignupResult signup = signupNew("empty01", "빈바디");
        mockMvc.perform(patch("/api/v1/members/me")
                        .header("Authorization", "Bearer " + signup.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void update_weakPassword_returns400ValidationError() throws Exception {
        SignupResult signup = signupNew("weak01", "약한");
        mockMvc.perform(patch("/api/v1/members/me")
                        .header("Authorization", "Bearer " + signup.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"weak\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void softDelete_cascadesScheduleDeletion() throws Exception {
        // 회원가입 + 일정 1건 등록 (NoOpRouteService default → routeStatus PENDING_RETRY OK)
        SignupResult signup = signupNew("cascade01", "캐스케이드");
        String authHeader = "Bearer " + signup.accessToken();

        java.time.OffsetDateTime arrival = java.time.OffsetDateTime.now(java.time.ZoneOffset.ofHours(9)).plusMinutes(60);
        java.time.OffsetDateTime depart = arrival.minusMinutes(30);
        String createBody = """
                {
                  "title": "탈퇴테스트",
                  "origin": {"name":"우이동","lat":37.6612,"lng":127.0124},
                  "destination": {"name":"국민대","lat":37.6103,"lng":126.9969},
                  "userDepartureTime": "%s",
                  "arrivalTime": "%s",
                  "reminderOffsetMinutes": 5
                }
                """.formatted(depart, arrival);

        mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        // 등록 직후 active schedule 1건 확인 (@SQLRestriction 자동 deleted_at IS NULL 필터)
        assertThat(scheduleRepository.count()).isEqualTo(1L);

        // 회원 탈퇴 → cascade로 schedule도 soft-delete
        mockMvc.perform(delete("/api/v1/members/me")
                        .header("Authorization", authHeader))
                .andExpect(status().isNoContent());

        // active schedule 0건 — Issue #8 회귀 가드
        assertThat(scheduleRepository.count()).isEqualTo(0L);
    }

    @Autowired com.todayway.backend.schedule.repository.ScheduleRepository scheduleRepository;

    private record SignupResult(String accessToken, String refreshToken, String memberId) {}

    private SignupResult signupNew(String loginId, String nickname) throws Exception {
        SignupRequest signupReq = new SignupRequest(loginId, "P@ssw0rd!", nickname);
        String resp = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(resp).path("data");
        return new SignupResult(
                node.path("accessToken").asText(),
                node.path("refreshToken").asText(),
                node.path("memberId").asText());
    }
}
