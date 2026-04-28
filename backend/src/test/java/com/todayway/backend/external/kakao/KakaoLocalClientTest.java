package com.todayway.backend.external.kakao;

import com.todayway.backend.external.ExternalApiException;
import com.todayway.backend.external.kakao.dto.KakaoLocalSearchResponse;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * KakaoLocalClient 단위 테스트 (MockRestServiceServer).
 *
 * 검증 포인트:
 * 1. Authorization 헤더 "KakaoAK {apiKey}" 형식
 * 2. 응답 record 필드 매핑 (place_name → placeName, x/y string)
 * 3. 빈 body 가드 (NPE 방지)
 * 4. 모든 catch 블록에서 cause == null
 */
class KakaoLocalClientTest {

    private static final String API_KEY = "kakao-test-key-xyz";

    private MockRestServiceServer server;
    private KakaoLocalClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://dapi.kakao.com/v2/local")
                .defaultHeader("Authorization", "KakaoAK " + API_KEY);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoLocalClient(builder.build());
    }

    @Test
    void Authorization_헤더는_KakaoAK_prefix이고_응답_record_매핑이_정확하다() {
        String responseBody = """
                {
                  "meta": {"total_count": 1, "pageable_count": 1, "is_end": true},
                  "documents": [{
                    "id": "12345",
                    "place_name": "국민대학교",
                    "address_name": "서울 성북구 정릉동",
                    "road_address_name": "서울 성북구 정릉로 77",
                    "category_group_code": "SC4",
                    "x": "126.9970",
                    "y": "37.6107"
                  }]
                }
                """;
        server.expect(requestTo(Matchers.containsString("query=")))
                .andExpect(header("Authorization", "KakaoAK " + API_KEY))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        KakaoLocalSearchResponse res = client.searchKeyword("국민대학교");

        assertThat(res).isNotNull();
        assertThat(res.meta().totalCount()).isEqualTo(1);
        assertThat(res.documents()).hasSize(1);
        KakaoLocalSearchResponse.Document doc = res.documents().get(0);
        assertThat(doc.id()).isEqualTo("12345");
        assertThat(doc.placeName()).isEqualTo("국민대학교");
        assertThat(doc.roadAddressName()).isEqualTo("서울 성북구 정릉로 77");
        // x/y가 string으로 들어옴 (명세 §8.1 보강 — 후속 /geocode에서 Double.parseDouble)
        assertThat(doc.x()).isEqualTo("126.9970");
        assertThat(doc.y()).isEqualTo("37.6107");
        server.verify();
    }

    @Test
    void 빈_응답이면_API_FAILED_던지고_cause는_null() {
        // Kakao가 204 No Content 또는 빈 body 줄 때 NPE 방지
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        ExternalApiException ex = catchThrowableOfType(
                ExternalApiException.class,
                () -> client.searchKeyword("test"));

        assertThat(ex).isNotNull();
        assertThat(ex.getType()).isEqualTo(ExternalApiException.Type.API_FAILED);
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void HTTP_401이면_API_FAILED와_httpStatus_보존_cause는_null() {
        // Kakao 키 만료/미설정 시
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        ExternalApiException ex = catchThrowableOfType(
                ExternalApiException.class,
                () -> client.searchKeyword("test"));

        assertThat(ex).isNotNull();
        assertThat(ex.getType()).isEqualTo(ExternalApiException.Type.API_FAILED);
        assertThat(ex.getHttpStatus()).isEqualTo(401);
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void HTTP_500이면_API_FAILED_cause는_null() {
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        ExternalApiException ex = catchThrowableOfType(
                ExternalApiException.class,
                () -> client.searchKeyword("test"));

        assertThat(ex.getType()).isEqualTo(ExternalApiException.Type.API_FAILED);
        assertThat(ex.getHttpStatus()).isEqualTo(500);
        assertThat(ex.getCause()).isNull();
    }
}
