package com.todayway.backend.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todayway.backend.auth.dto.SignupRequest;
import com.todayway.backend.push.domain.PushSubscription;
import com.todayway.backend.push.repository.PushSubscriptionRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #9 회귀 가드 — {@code MemberService.softDelete} cascade 가
 * {@code push_subscription.revoked_at} 을 일괄 갱신하는지 검증.
 *
 * <p>시나리오 (이슈 #9 본문 그대로):
 * <ol>
 *   <li>회원가입 → access token</li>
 *   <li>push 구독 등록 (다중 디바이스 시나리오 — 2건)</li>
 *   <li>회원 탈퇴 ({@code DELETE /api/v1/members/me})</li>
 *   <li>DB에서 모든 활성 구독이 {@code revoked_at IS NOT NULL} 인지 검증</li>
 * </ol>
 *
 * <p>이 테스트는 코드가 cascade를 실수로 빼먹어도 (DB FK CASCADE 는 hard-delete 에서만 동작 —
 * soft-delete 환경에선 코드가 유일한 보호선) 즉시 실패해 silent 회귀를 차단한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MemberSoftDeleteCascadeTest {

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
    @Autowired PushSubscriptionRepository subscriptionRepository;

    @Test
    void 회원_탈퇴시_모든_활성_push_subscription_revoke_cascade() throws Exception {
        // 1) 가입
        String token = signupAndGetToken("cascade01", "탈퇴자");

        // 2) 다중 디바이스 시뮬레이션 — 2건 구독
        subscribe(token, "https://fcm.googleapis.com/fcm/send/cascade-pc");
        subscribe(token, "https://fcm.googleapis.com/fcm/send/cascade-mobile");

        long activeBefore = subscriptionRepository.findAll().stream()
                .filter(PushSubscription::isActive)
                .count();
        assertEquals(2L, activeBefore, "탈퇴 전 2건 활성");

        // 3) 회원 탈퇴
        mockMvc.perform(delete("/api/v1/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // 4) cascade 검증 — 모든 row 의 revoked_at non-null
        for (PushSubscription sub : subscriptionRepository.findAll()) {
            assertFalse(sub.isActive(),
                    "회원 탈퇴 cascade — subscriptionUid=" + sub.getSubscriptionUid()
                            + " 가 revoke 되어야 함 (issue #9)");
            assertNotNull(sub.getRevokedAt(), "revoked_at 시각이 박혀야 함");
        }
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
}
