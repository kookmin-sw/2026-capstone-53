package com.todayway.backend.external.odsay;

import com.todayway.backend.external.ExternalApiException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * OdsayClient 단위 테스트 (MockRestServiceServer).
 *
 * 핵심 회귀 방지:
 * 1. apiKey strict encoding ('+', '/', '=' → %2B, %2F, %3D) — queryParam 패턴으로 되돌리면 깨짐
 * 2. 모든 catch 블록에서 cause == null (stack trace 우회 누출 차단)
 * 3. 메시지에 URL/apiKey 미노출
 */
class OdsayClientTest {

    private MockRestServiceServer server;
    private OdsayClient client;

    @BeforeEach
    void setUp() {
        OdsayProperties props = new OdsayProperties();
        // '+', '/', '=' 가 strict encoding 되는지 검증하기 위해 의도적으로 포함
        props.setApiKey("test+key/with=special");
        props.setBaseUrl("https://api.odsay.com/v1/api");
        props.setTimeoutSeconds(5);

        RestClient.Builder builder = RestClient.builder().baseUrl(props.getBaseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OdsayClient(props, builder.build());
    }

    @Test
    void apiKey의_특수문자가_strict_encoding된다() {
        // 회귀 방지: 누가 .queryParam("apiKey", ...)로 되돌리면 '+'가 그대로 가서 ODsay 401.
        // URI 템플릿 변수 확장은 reserved char를 모두 인코딩.
        server.expect(requestTo(Matchers.containsString("apiKey=test%2Bkey%2Fwith%3Dspecial")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"result\":{\"path\":[]}}", MediaType.APPLICATION_JSON));

        String result = client.searchPubTransPathT(126.997, 37.611, 126.978, 37.5665);

        assertThat(result).contains("path");
        server.verify();
    }

    @Test
    void 좌표_4개가_쿼리스트링에_정확히_담긴다() {
        server.expect(requestTo(Matchers.allOf(
                        Matchers.containsString("SX=126.997"),
                        Matchers.containsString("SY=37.611"),
                        Matchers.containsString("EX=126.978"),
                        Matchers.containsString("EY=37.5665"))))
                .andRespond(withSuccess("{\"result\":{}}", MediaType.APPLICATION_JSON));

        client.searchPubTransPathT(126.997, 37.611, 126.978, 37.5665);

        server.verify();
    }

    @Test
    void 빈_응답_body면_SERVER_ERROR_던지고_cause는_null() {
        // 빈 body는 외부 응답 비정상 → SERVER_ERROR (재시도 가치 있음)
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        ExternalApiException ex = catchThrowableOfType(
                ExternalApiException.class,
                () -> client.searchPubTransPathT(126.997, 37.611, 126.978, 37.5665));

        assertThat(ex).isNotNull();
        assertThat(ex.getType()).isEqualTo(ExternalApiException.Type.SERVER_ERROR);
        assertThat(ex.getSource()).isEqualTo(ExternalApiException.Source.ODSAY);
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void HTTP_401이면_CLIENT_ERROR와_httpStatus_보존_cause는_null() {
        // ODsay가 키 만료 시 → CLIENT_ERROR (요청 자체 문제, 재시도 무의미)
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("ApiKeyAuthFailed"));

        ExternalApiException ex = catchThrowableOfType(
                ExternalApiException.class,
                () -> client.searchPubTransPathT(126.997, 37.611, 126.978, 37.5665));

        assertThat(ex).isNotNull();
        assertThat(ex.getType()).isEqualTo(ExternalApiException.Type.CLIENT_ERROR);
        assertThat(ex.getHttpStatus()).isEqualTo(401);
        // 보안 회귀 방지: cause로 RestClientResponseException 보존하면 stack trace에 URL+apiKey 누출
        assertThat(ex.getCause()).isNull();
        // 보안 회귀 방지: 메시지에 URL/apiKey 박히면 안 됨
        assertThat(ex.getMessage())
                .doesNotContain("apiKey")
                .doesNotContain("test+key")
                .doesNotContain("https://");
    }

    @Test
    void HTTP_500이면_SERVER_ERROR와_httpStatus_보존() {
        // 외부 일시 장애 → SERVER_ERROR (재시도 가치 있음)
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        ExternalApiException ex = catchThrowableOfType(
                ExternalApiException.class,
                () -> client.searchPubTransPathT(126.997, 37.611, 126.978, 37.5665));

        assertThat(ex).isNotNull();
        assertThat(ex.getType()).isEqualTo(ExternalApiException.Type.SERVER_ERROR);
        assertThat(ex.getHttpStatus()).isEqualTo(500);
        assertThat(ex.getCause()).isNull();
    }

    // ─── loadLane (§6.1 v1.1.10) ─────────────────────────────────

    @Test
    void loadLane_정상_200_응답_mapObject_prefix는_raw_mapObj변수는_strict_encoding() {
        // 회귀 가드: 템플릿 "0:0@"는 raw 박혀있어 percent-encoding 안 됨
        // (Spring RestClient default EncodingMode.TEMPLATE_AND_VALUES).
        // 변수 {mapObj}만 strict — "908:1:1:16"의 ':' 모두 %3A로 변환.
        // 누가 .queryParam("mapObject", "0:0@" + mapObj) 패턴으로 바꾸면 prefix까지 인코딩되어
        // ODsay에 다른 형태로 전달됨 — 그 회귀 차단.
        server.expect(requestTo(Matchers.allOf(
                        Matchers.containsString("/loadLane"),
                        // prefix는 raw — '0:0@'가 percent-encoded 안 됨
                        Matchers.containsString("mapObject=0:0@"),
                        // mapObj 변수는 strict — '908:1:1:16' → '908%3A1%3A1%3A16'
                        Matchers.containsString("908%3A1%3A1%3A16"),
                        // apiKey도 strict
                        Matchers.containsString("apiKey=test%2Bkey%2Fwith%3Dspecial"))))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"result\":{\"lane\":[]}}", MediaType.APPLICATION_JSON));

        String result = client.loadLane("908:1:1:16");

        assertThat(result).contains("lane");
        server.verify();
    }

    @Test
    void loadLane_빈_응답_body면_SERVER_ERROR_cause_null() {
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        ExternalApiException ex = catchThrowableOfType(
                ExternalApiException.class, () -> client.loadLane("908:1:1:16"));

        assertThat(ex).isNotNull();
        assertThat(ex.getType()).isEqualTo(ExternalApiException.Type.SERVER_ERROR);
        assertThat(ex.getSource()).isEqualTo(ExternalApiException.Source.ODSAY);
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void loadLane_HTTP_401이면_CLIENT_ERROR와_httpStatus_보존_cause_null() {
        // 운영자 alert: searchPubTransPathT와 동일 정책 — 401/403은 503 EXTERNAL_AUTH_MISCONFIGURED로 격상
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("ApiKeyAuthFailed"));

        ExternalApiException ex = catchThrowableOfType(
                ExternalApiException.class, () -> client.loadLane("908:1:1:16"));

        assertThat(ex).isNotNull();
        assertThat(ex.getType()).isEqualTo(ExternalApiException.Type.CLIENT_ERROR);
        assertThat(ex.getHttpStatus()).isEqualTo(401);
        assertThat(ex.getCause()).isNull();
        assertThat(ex.getMessage())
                .doesNotContain("apiKey")
                .doesNotContain("test+key")
                .doesNotContain("https://");
    }

    @Test
    void loadLane_HTTP_500이면_SERVER_ERROR와_httpStatus_보존() {
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        ExternalApiException ex = catchThrowableOfType(
                ExternalApiException.class, () -> client.loadLane("908:1:1:16"));

        assertThat(ex).isNotNull();
        assertThat(ex.getType()).isEqualTo(ExternalApiException.Type.SERVER_ERROR);
        assertThat(ex.getHttpStatus()).isEqualTo(500);
        assertThat(ex.getCause()).isNull();
    }
}
