package com.todayway.backend.external.tmap;

import com.todayway.backend.external.ExternalApiException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 명세 §6.1 v1.1.21 — TmapClient 단위 테스트 (MockRestServiceServer).
 *
 * 회귀 방지:
 * 1. POST 메서드 + appKey 헤더 (TMAP 공식 규약)
 * 2. {@code routes/pedestrian?version=1&format=json} 정확한 URI
 * 3. 모든 catch 에서 cause == null (응답 body 누출 차단), 메시지에 좌표/키 없음
 * 4. {@code isConfigured()} — 빈/null appKey 일 때 false
 */
class TmapClientTest {

    private MockRestServiceServer server;
    private TmapClient client;

    @BeforeEach
    void setUp() {
        TmapProperties props = new TmapProperties();
        props.setAppKey("tmap-test-app-key");
        props.setBaseUrl("https://apis.openapi.sk.com/tmap");
        props.setTimeoutSeconds(5);

        RestClient.Builder builder = RestClient.builder().baseUrl(props.getBaseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TmapClient(props, builder.build());
    }

    @Test
    void POST_routes_pedestrian_appKey_헤더_정합() {
        server.expect(requestTo(Matchers.containsString("/routes/pedestrian?version=1&format=json")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("appKey", "tmap-test-app-key"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, Matchers.containsString("application/json")))
                .andRespond(withSuccess(
                        "{\"type\":\"FeatureCollection\",\"features\":[]}",
                        MediaType.APPLICATION_JSON));

        String body = client.routesPedestrian(126.9969, 37.6103, 127.0124, 37.6612);

        assertThat(body).contains("FeatureCollection");
        server.verify();
    }

    @Test
    void 빈_응답_body면_SERVER_ERROR_던지고_cause는_null() {
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        ExternalApiException ex = catchThrowableOfType(
                ExternalApiException.class,
                () -> client.routesPedestrian(126.99, 37.61, 127.0, 37.66));

        assertThat(ex).isNotNull();
        assertThat(ex.getType()).isEqualTo(ExternalApiException.Type.SERVER_ERROR);
        assertThat(ex.getSource()).isEqualTo(ExternalApiException.Source.TMAP);
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void HTTP_403이면_CLIENT_ERROR와_httpStatus_보존_cause는_null_좌표키_미노출() {
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("INVALID_API_KEY"));

        ExternalApiException ex = catchThrowableOfType(
                ExternalApiException.class,
                () -> client.routesPedestrian(126.99, 37.61, 127.0, 37.66));

        assertThat(ex).isNotNull();
        assertThat(ex.getType()).isEqualTo(ExternalApiException.Type.CLIENT_ERROR);
        assertThat(ex.getHttpStatus()).isEqualTo(403);
        assertThat(ex.getCause()).isNull();
        assertThat(ex.getMessage())
                .doesNotContain("appKey")
                .doesNotContain("tmap-test-app-key")
                .doesNotContain("https://");
    }

    @Test
    void HTTP_401이면_CLIENT_ERROR_분류() {
        // 키 만료/invalid — graceful fallback 분기 (mapper 가 catch 후 v1.1.9 직선 반환)
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("AUTH_FAILED"));

        ExternalApiException ex = catchThrowableOfType(
                ExternalApiException.class,
                () -> client.routesPedestrian(126.99, 37.61, 127.0, 37.66));

        assertThat(ex).isNotNull();
        assertThat(ex.getType()).isEqualTo(ExternalApiException.Type.CLIENT_ERROR);
        assertThat(ex.getHttpStatus()).isEqualTo(401);
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void appKey_미설정_시_routesPedestrian_즉시_CLIENT_ERROR_throw_NPE_방지() {
        // M2 — caller 가 isConfigured() 검증 우회해 직접 호출 시 RestClient.header(name, null) 의
        // NPE 방지. graceful 정상화 — mapper catch 후 fallback.
        TmapProperties unconfigured = new TmapProperties();
        unconfigured.setBaseUrl("https://apis.openapi.sk.com/tmap");
        // appKey 빈 문자열 (또는 null) — isConfigured()=false
        TmapClient noKeyClient = new TmapClient(unconfigured, RestClient.builder().build());

        ExternalApiException ex = catchThrowableOfType(
                ExternalApiException.class,
                () -> noKeyClient.routesPedestrian(126.99, 37.61, 127.0, 37.66));

        assertThat(ex).isNotNull();
        assertThat(ex.getType()).isEqualTo(ExternalApiException.Type.CLIENT_ERROR);
        assertThat(ex.getSource()).isEqualTo(ExternalApiException.Source.TMAP);
        assertThat(ex.getCause()).isNull();
        // 실 RestClient 호출 X (HTTP 트래픽 0, 본 테스트는 server stub 도 X)
    }

    @Test
    void HTTP_500이면_SERVER_ERROR_재시도_가능_분류() {
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        ExternalApiException ex = catchThrowableOfType(
                ExternalApiException.class,
                () -> client.routesPedestrian(126.99, 37.61, 127.0, 37.66));

        assertThat(ex).isNotNull();
        assertThat(ex.getType()).isEqualTo(ExternalApiException.Type.SERVER_ERROR);
        assertThat(ex.getHttpStatus()).isEqualTo(500);
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void SocketTimeoutException은_TIMEOUT_분류_L5() {
        // L5 — ResourceAccessException 의 root cause 가 SocketTimeoutException 이면 TIMEOUT 분류.
        // 운영 모니터링에서 timeout 알림 / 재시도 정책 분기에 사용. 분류가 깨져도 mapper 가 catch
        // 해서 사용자 영향 없지만, 메트릭/알람 회귀 가드.
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(req -> { throw new SocketTimeoutException("read timeout"); });

        ExternalApiException ex = catchThrowableOfType(
                ExternalApiException.class,
                () -> client.routesPedestrian(126.99, 37.61, 127.0, 37.66));

        assertThat(ex).isNotNull();
        assertThat(ex.getType()).isEqualTo(ExternalApiException.Type.TIMEOUT);
        assertThat(ex.getSource()).isEqualTo(ExternalApiException.Source.TMAP);
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void isConfigured_빈_키_또는_null_이면_false() {
        TmapProperties empty = new TmapProperties();
        empty.setAppKey("");
        empty.setBaseUrl("https://apis.openapi.sk.com/tmap");
        TmapClient unconfigured = new TmapClient(empty, RestClient.builder().build());
        assertThat(unconfigured.isConfigured()).isFalse();

        TmapProperties whitespace = new TmapProperties();
        whitespace.setAppKey("   ");
        whitespace.setBaseUrl("https://apis.openapi.sk.com/tmap");
        TmapClient whitespaceClient = new TmapClient(whitespace, RestClient.builder().build());
        assertThat(whitespaceClient.isConfigured()).isFalse();

        // 본 테스트 클래스의 setUp 에서 키 박혀있어 true
        assertThat(client.isConfigured()).isTrue();
    }
}
