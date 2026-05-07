package com.todayway.backend.geocode.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todayway.backend.auth.dto.SignupRequest;
import com.todayway.backend.external.ExternalApiException;
import com.todayway.backend.external.kakao.KakaoLocalClient;
import com.todayway.backend.external.kakao.dto.KakaoLocalSearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 명세 §8.1 — POST /geocode 통합 테스트.
 *
 * <p>{@link KakaoLocalClient} 는 {@code @MockitoBean} — 외부 호출 격리, 매핑/캐시 흐름만 검증.
 *
 * <p>회귀 가드 매트릭스:
 * <ul>
 *   <li>정상 매치 / 캐시 hit (Kakao 호출 1회)</li>
 *   <li>미스 → 404 / 미스 캐시 hit</li>
 *   <li>401 → 503 / 5xx → 502 / timeout → 504 (명세 §8.1 매핑표)</li>
 *   <li>인증 401 / query blank → 400</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class GeocodeControllerIntegrationTest {

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

    @MockitoBean KakaoLocalClient kakaoLocalClient;

    @Test
    void 정상_매치_200_GeocodeResponse_v1_1_4_매핑표_정합() throws Exception {
        when(kakaoLocalClient.searchKeyword(any())).thenReturn(matchedResponse());

        String token = signupAndGetToken("geo01", "정상");

        mockMvc.perform(post("/api/v1/geocode")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"국민대학교 geo01\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched").value(true))
                .andExpect(jsonPath("$.data.name").value("국민대학교"))
                // road_address_name 우선 (명세 §8.1 v1.1.4)
                .andExpect(jsonPath("$.data.address").value("서울 성북구 정릉로 77"))
                .andExpect(jsonPath("$.data.lat").value(37.6103))
                .andExpect(jsonPath("$.data.lng").value(126.9969))
                .andExpect(jsonPath("$.data.placeId").value("1234567"))
                .andExpect(jsonPath("$.data.provider").value("KAKAO_LOCAL"));
    }

    @Test
    void 캐시_hit_같은_query_두번째_호출시_Kakao_X_verify_times_1() throws Exception {
        when(kakaoLocalClient.searchKeyword(any())).thenReturn(matchedResponse());
        String token = signupAndGetToken("geocache01", "캐시");

        // 1차 호출 — Kakao 호출 + 캐시 INSERT
        callGeocode(token, "국민대학교 cache01");
        // 2차 호출 — TTL 안 → cache hit, Kakao 호출 X
        callGeocode(token, "국민대학교 cache01");

        verify(kakaoLocalClient, times(1)).searchKeyword(any());
    }

    @Test
    void road_address_빈값이면_address_name_fallback() throws Exception {
        // 명세 §8.1 v1.1.4 — road_address_name 빈값이면 address_name fallback.
        KakaoLocalSearchResponse.Document doc = new KakaoLocalSearchResponse.Document(
                "9876", "어떤장소", "서울 성북구 정릉동 100", "", "", "127.001", "37.600");
        KakaoLocalSearchResponse resp = new KakaoLocalSearchResponse(
                new KakaoLocalSearchResponse.Meta(1, 1, true), List.of(doc));
        when(kakaoLocalClient.searchKeyword(any())).thenReturn(resp);

        String token = signupAndGetToken("geofallback01", "fallback");

        mockMvc.perform(post("/api/v1/geocode")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"어떤장소 fb01\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address").value("서울 성북구 정릉동 100"));
    }

    @Test
    void documents_빈배열_404_GEOCODE_NO_MATCH() throws Exception {
        when(kakaoLocalClient.searchKeyword(any())).thenReturn(emptyResponse());

        String token = signupAndGetToken("geomiss01", "미스");

        mockMvc.perform(post("/api/v1/geocode")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"asdfasdfasdf miss01\" }"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GEOCODE_NO_MATCH"));
    }

    @Test
    void 미스_캐시_hit_두번째_호출시_Kakao_X_verify_times_1() throws Exception {
        when(kakaoLocalClient.searchKeyword(any())).thenReturn(emptyResponse());
        String token = signupAndGetToken("geomisscache01", "미스캐시");

        // 1차 — Kakao 호출 + miss row INSERT + 404
        mockMvc.perform(post("/api/v1/geocode")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"asdf misscache01\" }"))
                .andExpect(status().isNotFound());
        // 2차 — cache hit (matched=false) → 404, Kakao 호출 X
        mockMvc.perform(post("/api/v1/geocode")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"asdf misscache01\" }"))
                .andExpect(status().isNotFound());

        verify(kakaoLocalClient, times(1)).searchKeyword(any());
    }

    @Test
    void Kakao_401_503_EXTERNAL_AUTH_MISCONFIGURED() throws Exception {
        when(kakaoLocalClient.searchKeyword(any())).thenThrow(new ExternalApiException(
                ExternalApiException.Source.KAKAO_LOCAL,
                ExternalApiException.Type.CLIENT_ERROR, 401, "401", null));
        String token = signupAndGetToken("geo401", "auth");

        mockMvc.perform(post("/api/v1/geocode")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"q401\" }"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("EXTERNAL_AUTH_MISCONFIGURED"));
    }

    @Test
    void Kakao_5xx_502_EXTERNAL_ROUTE_API_FAILED() throws Exception {
        when(kakaoLocalClient.searchKeyword(any())).thenThrow(new ExternalApiException(
                ExternalApiException.Source.KAKAO_LOCAL,
                ExternalApiException.Type.SERVER_ERROR, 502, "502", null));
        String token = signupAndGetToken("geo502", "5xx");

        mockMvc.perform(post("/api/v1/geocode")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"q502\" }"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("EXTERNAL_ROUTE_API_FAILED"));
    }

    @Test
    void Kakao_timeout_504_EXTERNAL_TIMEOUT() throws Exception {
        when(kakaoLocalClient.searchKeyword(any())).thenThrow(new ExternalApiException(
                ExternalApiException.Source.KAKAO_LOCAL,
                ExternalApiException.Type.TIMEOUT, null, "timeout", null));
        String token = signupAndGetToken("geotimeout", "timeout");

        mockMvc.perform(post("/api/v1/geocode")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"qtimeout\" }"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error.code").value("EXTERNAL_TIMEOUT"));
    }

    @Test
    void 인증_없이_geocode_401() throws Exception {
        mockMvc.perform(post("/api/v1/geocode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"국민대\" }"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void query_blank이면_400_VALIDATION_ERROR() throws Exception {
        String token = signupAndGetToken("geoval", "검증");

        mockMvc.perform(post("/api/v1/geocode")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
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

    private void callGeocode(String token, String query) throws Exception {
        mockMvc.perform(post("/api/v1/geocode")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"" + query + "\" }"))
                .andExpect(status().isOk());
    }

    private static KakaoLocalSearchResponse matchedResponse() {
        KakaoLocalSearchResponse.Document doc = new KakaoLocalSearchResponse.Document(
                "1234567",
                "국민대학교",
                "서울 성북구 정릉동 861-1",
                "서울 성북구 정릉로 77",
                "SC4",
                "126.9969",   // x = lng (Kakao string)
                "37.6103"     // y = lat
        );
        return new KakaoLocalSearchResponse(
                new KakaoLocalSearchResponse.Meta(1, 1, true), List.of(doc));
    }

    private static KakaoLocalSearchResponse emptyResponse() {
        return new KakaoLocalSearchResponse(
                new KakaoLocalSearchResponse.Meta(0, 0, true), List.of());
    }
}
