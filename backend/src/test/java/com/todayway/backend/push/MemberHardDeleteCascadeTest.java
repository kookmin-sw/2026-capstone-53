package com.todayway.backend.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todayway.backend.auth.dto.SignupRequest;
import com.todayway.backend.auth.repository.RefreshTokenRepository;
import com.todayway.backend.member.repository.MemberRepository;
import com.todayway.backend.push.repository.PushSubscriptionRepository;
import com.todayway.backend.schedule.repository.ScheduleRepository;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 명세 §3.3 v1.1.22 (이슈 #31) — 회원 hard delete cascade 회귀 가드.
 *
 * <p>{@code DELETE /api/v1/members/me} → {@code memberRepository.delete()} 후 DB FK ON DELETE
 * CASCADE 가 다음 row 들을 일괄 정리하는지 검증:
 * <ul>
 *   <li>{@code refresh_token} → 삭제 (활성 토큰 무효화)</li>
 *   <li>{@code schedule} → 삭제 (소유 일정 정리)</li>
 *   <li>{@code push_subscription} → 삭제 (모든 디바이스 구독 해제)</li>
 * </ul>
 *
 * <p>이전 soft delete 모델 (Issue #8/#9) 의 cascade 는 코드 cascade 메서드가 유일한 보호선이라
 * 회귀 시 silent 였지만, hard delete 모델에서는 DB FK 가 강제. 본 테스트는 그 강제력이
 * 통합 환경에서 실제로 동작하는지 확인.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MemberHardDeleteCascadeTest {

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
    @Autowired ObjectMapper objectMapper;
    @Autowired MemberRepository memberRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired ScheduleRepository scheduleRepository;
    @Autowired PushSubscriptionRepository subscriptionRepository;

    @Test
    void 회원_hard_delete시_refresh_token_schedule_push_subscription_모두_FK_CASCADE로_삭제() throws Exception {
        // 1) 가입 (refresh_token row 1건 자동 생성)
        String token = signupAndGetToken("cascade01", "탈퇴자");
        assertThat(memberRepository.count()).isEqualTo(1L);
        assertThat(refreshTokenRepository.count()).isEqualTo(1L);

        // 2) 일정 1건 등록
        createSchedule(token);
        assertThat(scheduleRepository.count()).isEqualTo(1L);

        // 3) 다중 디바이스 시뮬레이션 — push 구독 2건
        subscribe(token, "https://fcm.googleapis.com/fcm/send/cascade-pc");
        subscribe(token, "https://fcm.googleapis.com/fcm/send/cascade-mobile");
        assertThat(subscriptionRepository.count()).isEqualTo(2L);

        // 4) 회원 탈퇴 → hard delete
        mockMvc.perform(delete("/api/v1/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // 5) FK CASCADE 검증 — 모든 도메인 row 가 DB 에서 사라짐 (soft 모델의 deleted_at IS NOT NULL 가 아님)
        assertThat(memberRepository.count())
                .as("member row 자체 삭제 (hard delete)").isEqualTo(0L);
        assertThat(refreshTokenRepository.count())
                .as("refresh_token CASCADE — row 삭제").isEqualTo(0L);
        assertThat(scheduleRepository.count())
                .as("schedule CASCADE — row 삭제").isEqualTo(0L);
        assertThat(subscriptionRepository.count())
                .as("push_subscription CASCADE — row 삭제 (옛 soft revoke 모델과 달리 row 자체 사라짐)")
                .isEqualTo(0L);
    }

    @Test
    void 회원_hard_delete_후_동일_loginId_재가입_가능() throws Exception {
        // 이슈 #31 핵심 회귀 가드 — soft delete + login_id UNIQUE 충돌 모델에서 409 던지던 경로가
        // hard delete 로 해소됨을 검증.
        String loginId = "rejoin01";
        String token = signupAndGetToken(loginId, "탈퇴자");

        mockMvc.perform(delete("/api/v1/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // 동일 loginId 로 재가입 → 201 (탈퇴 전엔 409 LOGIN_ID_DUPLICATED 였음)
        SignupRequest reJoin = new SignupRequest(loginId, "P@ssw0rd!", "재가입");
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reJoin)))
                .andExpect(status().isCreated());
    }

    private String signupAndGetToken(String loginId, String nickname) throws Exception {
        SignupRequest req = new SignupRequest(loginId, "P@ssw0rd!", nickname);
        String resp = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(resp).path("data");
        return node.path("accessToken").asText();
    }

    private void subscribe(String token, String endpoint) throws Exception {
        String body = """
                { "endpoint": "%s", "keys": { "p256dh": "BNc-cascade", "auth": "auth-cascade" } }
                """.formatted(endpoint);
        mockMvc.perform(post("/api/v1/push/subscribe")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private void createSchedule(String token) throws Exception {
        OffsetDateTime arrival = OffsetDateTime.now(ZoneOffset.ofHours(9)).plusMinutes(60);
        OffsetDateTime depart = arrival.minusMinutes(30);
        String body = """
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
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
