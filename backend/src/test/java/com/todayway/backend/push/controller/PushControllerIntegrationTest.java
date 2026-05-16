package com.todayway.backend.push.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todayway.backend.auth.dto.SignupRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 명세 §7.1 / §7.2 — Push 구독 등록·해제 통합 테스트.
 *
 * <p>회귀 가드: 인증/인가/sub_ prefix/UPSERT(reactivate)/타인 endpoint 403/없는 sub 404/
 * validation(@NotBlank endpoint, keys.p256dh, keys.auth).
 *
 * <p>{@code push.scheduler.enabled=false} — 통합 테스트 중 스케줄러 자동 트리거 차단.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PushControllerIntegrationTest {

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

    @Test
    void 정상_subscribe_201_sub_prefix_subscriptionId_반환() throws Exception {
        String token = signupAndGetToken("pushsub01", "구독자");

        mockMvc.perform(post("/api/v1/push/subscribe")
                        .header("Authorization", "Bearer " + token)
                        .header("User-Agent", "Mozilla/5.0 (Test)")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscribeBody("https://fcm.googleapis.com/fcm/send/test001",
                                "BNc-test-p256dh-key-1", "tBHI-auth-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subscriptionId").value(org.hamcrest.Matchers.startsWith("sub_")));
    }

    @Test
    void 동일_endpoint_재구독시_같은_subscriptionId_반환_revoked_at_재활성화() throws Exception {
        String token = signupAndGetToken("pushrearm01", "재활성화");
        String endpoint = "https://fcm.googleapis.com/fcm/send/rearm001";

        // 1차 구독
        String first = subscribe(token, endpoint, "BNc-key-1", "auth-1");

        // 2차 구독 (키 회전 시뮬레이션) — 같은 endpoint 재사용
        String second = subscribe(token, endpoint, "BNc-key-2", "auth-2");

        // 명세 §7.1 비고 — 동일 endpoint 재구독은 row 갱신, subscriptionId 보존
        org.junit.jupiter.api.Assertions.assertEquals(first, second);
    }

    @Test
    void 다른_회원이_동일_endpoint_재구독시_403_FORBIDDEN_RESOURCE() throws Exception {
        String tokenA = signupAndGetToken("pushown01", "원소유자");
        String endpoint = "https://fcm.googleapis.com/fcm/send/conflict001";
        subscribe(tokenA, endpoint, "BNc-A", "auth-A");

        String tokenB = signupAndGetToken("pushint01", "침입자");
        mockMvc.perform(post("/api/v1/push/subscribe")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscribeBody(endpoint, "BNc-B", "auth-B")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN_RESOURCE"));
    }

    @Test
    void 정상_unsubscribe_204_NoContent() throws Exception {
        String token = signupAndGetToken("pushrm01", "해제자");
        String subscriptionId = subscribe(token, "https://fcm.googleapis.com/fcm/send/rm001", "BNc", "auth");

        mockMvc.perform(delete("/api/v1/push/subscribe/" + subscriptionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void 다른_사용자의_subscriptionId_해제시도_403() throws Exception {
        String ownerToken = signupAndGetToken("pushrmown01", "구독소유자");
        String subscriptionId = subscribe(ownerToken,
                "https://fcm.googleapis.com/fcm/send/rmperm001", "BNc", "auth");

        String otherToken = signupAndGetToken("pushrmoth01", "다른사용자");
        mockMvc.perform(delete("/api/v1/push/subscribe/" + subscriptionId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN_RESOURCE"));
    }

    @Test
    void 존재하지_않는_subscriptionId_해제시_404_SUBSCRIPTION_NOT_FOUND() throws Exception {
        String token = signupAndGetToken("pushrm404", "없는구독");

        // v1.1.35 — strict ULID 검증 통과는 시키되 DB 에 없는 값. Crockford Base32 alphabet
        // (0-9 A-H J K M N P-T V-Z) 26자 정합. 기존엔 27자 + 금지문자 (I,O) 포함이었는데
        // strict 검증 도입 후 400 으로 떨어지는 무관한 신호가 되어 회귀 의도 변형.
        mockMvc.perform(delete("/api/v1/push/subscribe/sub_01HXXAAAAAAAAAAAAAAAAAAAAA")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SUBSCRIPTION_NOT_FOUND"));
    }

    /**
     * v1.1.35 — strict 검증: prefix 누락 / ULID 본문 길이 / 금지문자 (I,L,O,U) / 소문자 모두 400.
     * 이전엔 silent strip 으로 DB lookup 까지 흘러가 형식 위반 입력도 404 로 응답되던 결함.
     */
    @Test
    void 잘못된_형식_subscriptionId_해제시_400_VALIDATION_ERROR() throws Exception {
        String token = signupAndGetToken("pushrm400", "형식검증");

        String[] invalidIds = {
                "01HXXAAAAAAAAAAAAAAAAAAAAA",                  // prefix 누락
                "sch_01HXXAAAAAAAAAAAAAAAAAAAAA",              // 잘못된 prefix
                "sub_01HXX",                                   // 너무 짧음
                "sub_01HXXAAAAAAAAAAAAAAAAAAAAAA",             // 너무 김 (27자)
                "sub_01HXXIIIIIIIIIIIIIIIIIIIIII",             // I 금지문자
                "sub_01HXXOOOOOOOOOOOOOOOOOOOOOO",             // O 금지문자
                "sub_01hxxaaaaaaaaaaaaaaaaaaaaaa"              // 소문자
        };
        for (String bad : invalidIds) {
            mockMvc.perform(delete("/api/v1/push/subscribe/" + bad)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void 인증_없이_subscribe_401() throws Exception {
        mockMvc.perform(post("/api/v1/push/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscribeBody("https://fcm.googleapis.com/fcm/send/noauth", "BNc", "auth")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpoint_blank이면_400_VALIDATION_ERROR() throws Exception {
        String token = signupAndGetToken("pushval01", "검증");

        mockMvc.perform(post("/api/v1/push/subscribe")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscribeBody("", "BNc", "auth")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void keys_p256dh_blank이면_400_VALIDATION_ERROR() throws Exception {
        String token = signupAndGetToken("pushval02", "키검증");

        mockMvc.perform(post("/api/v1/push/subscribe")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscribeBody("https://fcm.googleapis.com/fcm/send/val02", "", "auth")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    /**
     * v1.1.32 §7.1 — endpoint scheme 가드: RFC 8030 위반 (http / file / data / javascript / 공백 포함)
     * 입력은 400 VALIDATION_ERROR. push provider 호출 단계로 새지 않게 fail-fast.
     */
    @Test
    void endpoint_https_아니면_400_VALIDATION_ERROR() throws Exception {
        String token = signupAndGetToken("pushval03", "스킴검증");

        String[] invalidEndpoints = {
                "http://fcm.googleapis.com/fcm/send/insecure",
                "file:///etc/passwd",
                "data:text/plain;base64,SGVsbG8=",
                "javascript:alert(1)",
                "https://has space in url/endpoint"
        };
        for (String bad : invalidEndpoints) {
            mockMvc.perform(post("/api/v1/push/subscribe")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(subscribeBody(bad, "BNc", "auth")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    // ───── helpers ─────

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

    private String subscribe(String token, String endpoint, String p256dh, String auth) throws Exception {
        String resp = mockMvc.perform(post("/api/v1/push/subscribe")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscribeBody(endpoint, p256dh, auth)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("subscriptionId").asText();
    }

    private static String subscribeBody(String endpoint, String p256dh, String auth) {
        return """
                {
                  "endpoint": "%s",
                  "keys": { "p256dh": "%s", "auth": "%s" }
                }
                """.formatted(endpoint, p256dh, auth);
    }
}
