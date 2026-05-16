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

import java.util.ArrayList;
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
                .andExpect(jsonPath("$.data.provider").value("KAKAO"));
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

    @Test
    void query_길이_256자_400_VALIDATION_ERROR() throws Exception {
        // @Size(max=255) — query_text VARCHAR(255) SQL truncation 회피용 1차 가드.
        String token = signupAndGetToken("geosize", "길이검증");
        String tooLong = "a".repeat(256);

        mockMvc.perform(post("/api/v1/geocode")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"" + tooLong + "\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ───── §8.2 v1.1.27 — POST /geocode/search 회귀 가드 ─────

    @Test
    void search_정상_다중_후보_size_default_10_적용() throws Exception {
        // §8.2 v1.1.27 — size 생략 시 default 10 (max 동일). documents 가 12건이라도 10건만 반환.
        when(kakaoLocalClient.searchKeyword(any())).thenReturn(multiResponse(12));
        String token = signupAndGetToken("geosearch01", "다중후보");

        mockMvc.perform(post("/api/v1/geocode/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"강남역\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates.length()").value(10))
                .andExpect(jsonPath("$.data.candidates[0].name").value("후보_0"))
                .andExpect(jsonPath("$.data.candidates[0].provider").value("KAKAO"));
    }

    @Test
    void search_documents_count_lt_default_있는만큼만_반환() throws Exception {
        // §8.2 — Kakao 가 3건만 돌려주면 default 10 cap 무관, 실제 후보 3건 그대로 반환.
        when(kakaoLocalClient.searchKeyword(any())).thenReturn(multiResponse(3));
        String token = signupAndGetToken("geosearchlt", "부분");

        mockMvc.perform(post("/api/v1/geocode/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"강남역\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates.length()").value(3));
    }

    @Test
    void search_size_명시_3건만_반환() throws Exception {
        when(kakaoLocalClient.searchKeyword(any())).thenReturn(multiResponse(10));
        String token = signupAndGetToken("geosearch02", "size명시");

        mockMvc.perform(post("/api/v1/geocode/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"강남역\", \"size\": 3 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates.length()").value(3));
    }

    @Test
    void search_documents_빈배열_404_GEOCODE_NO_MATCH() throws Exception {
        when(kakaoLocalClient.searchKeyword(any())).thenReturn(emptyResponse());
        String token = signupAndGetToken("geosearch03", "미스");

        mockMvc.perform(post("/api/v1/geocode/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"asdfasdf\" }"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GEOCODE_NO_MATCH"));
    }

    @Test
    void search_size_0_400_VALIDATION_ERROR() throws Exception {
        String token = signupAndGetToken("geosearch04", "size0");

        mockMvc.perform(post("/api/v1/geocode/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"강남\", \"size\": 0 }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void search_size_11_400_VALIDATION_ERROR() throws Exception {
        String token = signupAndGetToken("geosearch05", "size11");

        mockMvc.perform(post("/api/v1/geocode/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"강남\", \"size\": 11 }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void search_x_y_null_document_skip() throws Exception {
        // §8.2 — x/y null row 는 skip. 1건이라도 valid 면 200.
        KakaoLocalSearchResponse.Document bad = new KakaoLocalSearchResponse.Document(
                "bad", "Bad", "addr", "road", "SC4", null, null);
        KakaoLocalSearchResponse.Document good = new KakaoLocalSearchResponse.Document(
                "good", "Good", "addr", "서울 어딘가", "SC4", "127.001", "37.500");
        KakaoLocalSearchResponse resp = new KakaoLocalSearchResponse(
                new KakaoLocalSearchResponse.Meta(2, 2, true), List.of(bad, good));
        when(kakaoLocalClient.searchKeyword(any())).thenReturn(resp);
        String token = signupAndGetToken("geosearch06", "skip");

        mockMvc.perform(post("/api/v1/geocode/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"강남\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates.length()").value(1))
                .andExpect(jsonPath("$.data.candidates[0].name").value("Good"));
    }

    @Test
    void search_모든_document_invalid_502_EXTERNAL_ROUTE_API_FAILED() throws Exception {
        // §8.2 — limit 안 모든 row 가 invalid 면 502 (cf. v1.1.4 매핑표).
        KakaoLocalSearchResponse.Document bad = new KakaoLocalSearchResponse.Document(
                "bad", "Bad", "addr", "road", "SC4", "not-a-number", "not-a-number");
        KakaoLocalSearchResponse resp = new KakaoLocalSearchResponse(
                new KakaoLocalSearchResponse.Meta(1, 1, true), List.of(bad));
        when(kakaoLocalClient.searchKeyword(any())).thenReturn(resp);
        String token = signupAndGetToken("geosearch07", "전부invalid");

        mockMvc.perform(post("/api/v1/geocode/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"강남\" }"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("EXTERNAL_ROUTE_API_FAILED"));
    }

    @Test
    void search_캐시_미사용_같은_query_매번_Kakao_호출() throws Exception {
        // §8.2 — autocomplete hit ratio 가 낮아 cache 미사용. §8.1 캐시와 분리.
        when(kakaoLocalClient.searchKeyword(any())).thenReturn(multiResponse(3));
        String token = signupAndGetToken("geosearch08", "캐시미사용");

        mockMvc.perform(post("/api/v1/geocode/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"강남 노캐시\" }"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/geocode/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"강남 노캐시\" }"))
                .andExpect(status().isOk());

        verify(kakaoLocalClient, times(2)).searchKeyword(any());
    }

    @Test
    void search_인증_없이_401() throws Exception {
        mockMvc.perform(post("/api/v1/geocode/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"강남\" }"))
                .andExpect(status().isUnauthorized());
    }

    // ───── v1.1.37 회귀 가드 ─────

    @Test
    void search_x_y_NaN_Infinity_skip_finite_가드() throws Exception {
        // v1.1.37 P1#6 — Double.parseDouble 만으로는 "NaN"/"Infinity" 가 통과 → 프론트 지도 SDK
        // marker placement 폭주. GeocodeCandidate.from 의 isFinite 가드가 NumberFormatException
        // 으로 변환하면 caller 의 기존 skip 흐름에 합류. 1건이라도 valid 면 200.
        KakaoLocalSearchResponse.Document nan = new KakaoLocalSearchResponse.Document(
                "nan", "NaN후보", "addr", "road", "SC4", "NaN", "37.500");
        KakaoLocalSearchResponse.Document inf = new KakaoLocalSearchResponse.Document(
                "inf", "Inf후보", "addr", "road", "SC4", "127.001", "Infinity");
        KakaoLocalSearchResponse.Document good = new KakaoLocalSearchResponse.Document(
                "good", "정상후보", "addr", "서울 어딘가", "SC4", "127.001", "37.500");
        KakaoLocalSearchResponse resp = new KakaoLocalSearchResponse(
                new KakaoLocalSearchResponse.Meta(3, 3, true), List.of(nan, inf, good));
        when(kakaoLocalClient.searchKeyword(any())).thenReturn(resp);
        String token = signupAndGetToken("geosearchnan", "NaNInf");

        mockMvc.perform(post("/api/v1/geocode/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"강남\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates.length()").value(1))
                .andExpect(jsonPath("$.data.candidates[0].name").value("정상후보"));
    }

    @Test
    void geocode_KakaoLocalClient_RuntimeException_시_502_EXTERNAL_ROUTE_API_FAILED() throws Exception {
        // v1.1.37 P1#7 — KakaoLocalClient 가 ExternalApiException 외 unchecked (deserialize/parse
        // 등 IllegalStateException) 를 던지면 기존엔 GlobalExceptionHandler.handleUnknown 이 500 으로
        // 떨어뜨렸음. callKakao 의 RuntimeException catch 가 명세 §8.1 매핑표 502 로 흡수.
        when(kakaoLocalClient.searchKeyword(any()))
                .thenThrow(new IllegalStateException("Jackson deserialize 실패 simulated"));
        String token = signupAndGetToken("geounchk", "unchecked");

        mockMvc.perform(post("/api/v1/geocode")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"강남\" }"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("EXTERNAL_ROUTE_API_FAILED"));
    }

    @Test
    void search_size_cap_은_valid_후보_개수_invalid_skip_은_size_미차감() throws Exception {
        // v1.1.37 P1#11 — size 의미 정정. size=3, 앞 2건 invalid + 뒤 5건 valid → 응답 3건 (정상)
        // 이전 구현은 documents[0..3) 만 훑어 1건만 반환되던 결함.
        KakaoLocalSearchResponse.Document bad1 = new KakaoLocalSearchResponse.Document(
                "b1", "Bad1", "addr", "road", "SC4", null, "37.5");
        KakaoLocalSearchResponse.Document bad2 = new KakaoLocalSearchResponse.Document(
                "b2", "Bad2", "addr", "road", "SC4", "not-num", "37.5");
        KakaoLocalSearchResponse.Document g1 = new KakaoLocalSearchResponse.Document(
                "g1", "Good1", "addr", "road", "SC4", "127.001", "37.501");
        KakaoLocalSearchResponse.Document g2 = new KakaoLocalSearchResponse.Document(
                "g2", "Good2", "addr", "road", "SC4", "127.002", "37.502");
        KakaoLocalSearchResponse.Document g3 = new KakaoLocalSearchResponse.Document(
                "g3", "Good3", "addr", "road", "SC4", "127.003", "37.503");
        KakaoLocalSearchResponse.Document g4 = new KakaoLocalSearchResponse.Document(
                "g4", "Good4", "addr", "road", "SC4", "127.004", "37.504");
        KakaoLocalSearchResponse resp = new KakaoLocalSearchResponse(
                new KakaoLocalSearchResponse.Meta(6, 6, true),
                List.of(bad1, bad2, g1, g2, g3, g4));
        when(kakaoLocalClient.searchKeyword(any())).thenReturn(resp);
        String token = signupAndGetToken("geosizecap", "sizecap");

        mockMvc.perform(post("/api/v1/geocode/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"query\": \"강남\", \"size\": 3 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates.length()").value(3))
                .andExpect(jsonPath("$.data.candidates[0].name").value("Good1"))
                .andExpect(jsonPath("$.data.candidates[1].name").value("Good2"))
                .andExpect(jsonPath("$.data.candidates[2].name").value("Good3"));
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

    /** §8.2 — N 개의 valid 후보를 가진 Kakao Local 응답. id/이름은 순번, 좌표는 서울 인근 dummy. */
    private static KakaoLocalSearchResponse multiResponse(int n) {
        List<KakaoLocalSearchResponse.Document> docs = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            docs.add(new KakaoLocalSearchResponse.Document(
                    "id_" + i,
                    "후보_" + i,
                    "서울 어딘가 " + i,
                    "서울 도로명 " + i,
                    "SC4",
                    String.valueOf(127.000 + i * 0.001),
                    String.valueOf(37.500 + i * 0.001)
            ));
        }
        return new KakaoLocalSearchResponse(
                new KakaoLocalSearchResponse.Meta(n, n, true), docs);
    }
}
