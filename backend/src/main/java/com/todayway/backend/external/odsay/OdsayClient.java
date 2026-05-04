package com.todayway.backend.external.odsay;

import com.todayway.backend.external.ExternalApiException;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;

/**
 * ODsay 대중교통 길찾기 / 노선 그래픽 API 클라이언트. 명세 §5.1 / §6.1 v1.1.10 —
 * raw JSON을 호출자에게 그대로 반환. wrapped 저장 형식
 * ({@code {"path":..., "lane":...}})은 {@code OdsayRouteService} 책임.
 */
@Component
public class OdsayClient {

    private static final Logger log = LoggerFactory.getLogger(OdsayClient.class);
    private static final ExternalApiException.Source SOURCE = ExternalApiException.Source.ODSAY;

    private final OdsayProperties properties;
    private final RestClient restClient;

    @Autowired
    public OdsayClient(OdsayProperties properties) {
        this(properties, RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(createRequestFactory(properties.getTimeoutSeconds()))
                .build());
    }

    /** 테스트용. MockRestServiceServer로 모킹된 RestClient 주입을 위해 패키지 접근 허용.
     *  Spring 빈 생성자 후보에서 제외하기 위해 public 생성자에 @Autowired 명시함. */
    OdsayClient(OdsayProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    /**
     * Apache HttpClient 5 + 커넥션 풀 기반 RequestFactory.
     * SimpleClientHttpRequestFactory(HttpURLConnection) 대비 다중 호출 시 connection pooling으로 효율적.
     */
    private static ClientHttpRequestFactory createRequestFactory(int timeoutSeconds) {
        Timeout timeout = Timeout.ofSeconds(timeoutSeconds);
        HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(20)
                .setMaxConnPerRoute(10)
                .build();
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(timeout)
                        .setResponseTimeout(timeout)
                        .build())
                .build();
        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }

    /**
     * 대중교통 경로 검색.
     *
     * @param sx 출발지 경도 (longitude)
     * @param sy 출발지 위도 (latitude)
     * @param ex 도착지 경도
     * @param ey 도착지 위도
     * @return ODsay 응답 raw JSON 문자열 (route_summary_json에 그대로 저장)
     */
    public String searchPubTransPathT(double sx, double sy, double ex, double ey) {
        log.debug("ODsay 호출: SX={}, SY={}, EX={}, EY={}", sx, sy, ex, ey);
        try {
            // URI 템플릿 변수 확장으로 query value를 strict encoding (apiKey에 '+', '/' 포함되어 필요).
            String body = restClient.get()
                    .uri("/searchPubTransPathT?SX={sx}&SY={sy}&EX={ex}&EY={ey}&apiKey={apiKey}",
                            sx, sy, ex, ey, properties.getApiKey())
                    .retrieve()
                    .body(String.class);

            // ODsay가 빈 응답을 줄 때 후속 JSON 파싱 단계의 NPE 방지.
            if (body == null || body.isBlank()) {
                throw new ExternalApiException(SOURCE, ExternalApiException.Type.SERVER_ERROR,
                        null, "ODsay 응답 본문이 비어있음", null);
            }
            log.debug("ODsay 응답 수신: {} bytes", body.length());
            return body;
        } catch (RestClientResponseException e) {
            // 보안: cause는 응답 본문 일부를 메시지에 담을 수 있어 우회 누출 통로 → 보존 X.
            HttpStatusCode status = e.getStatusCode();
            ExternalApiException.Type type = status.is4xxClientError()
                    ? ExternalApiException.Type.CLIENT_ERROR
                    : ExternalApiException.Type.SERVER_ERROR;
            throw new ExternalApiException(SOURCE, type, status.value(),
                    "ODsay 호출 실패: HTTP " + status, null);
        } catch (ResourceAccessException e) {
            // cause chain 끝까지 가서 timeout 판정 (factory 교체 시 안전).
            Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(e);
            ExternalApiException.Type type = rootCause instanceof SocketTimeoutException
                    ? ExternalApiException.Type.TIMEOUT
                    : ExternalApiException.Type.NETWORK;
            // 보안: e.getMessage()는 URL+apiKey를 통째로 포함하고, cause로 보존하면 stack trace에서 우회 누출.
            String causeName = rootCause != null ? rootCause.getClass().getSimpleName() : "ResourceAccessException";
            throw new ExternalApiException(SOURCE, type, null, "ODsay 통신 실패 (" + causeName + ")", null);
        } catch (RestClientException e) {
            // 보안: 동일 사유로 cause 보존 X.
            throw new ExternalApiException(SOURCE, ExternalApiException.Type.NETWORK, null,
                    "ODsay 호출 중 예외 (" + e.getClass().getSimpleName() + ")", null);
        }
    }

    /**
     * 노선 그래픽 데이터 검색 — 명세 §6.1 비고. {@code searchPubTransPathT} 응답의
     * {@code result.path[0].info.mapObj} 값을 받아 실제 도로 곡선 좌표 응답을 반환.
     *
     * <p>호출 형식: {@code GET /loadLane?mapObject=0:0@{mapObj}&apiKey={key}}.
     * {@code "0:0@"} prefix는 ODsay 공식 문서에 명시되지 않은 *검증된 패턴*이다 (명세 §6.1 v1.1.10 비고).
     *
     * <p>호출자 정책 (명세 §6.1 v1.1.10):
     * <ul>
     *   <li>5xx/timeout/응답 형식 위반: graceful — caller가 catch해서 passStopList 직선 fallback</li>
     *   <li>401/403: 운영자 alert 필요 — caller가 propagate해서 503 EXTERNAL_AUTH_MISCONFIGURED 격상</li>
     * </ul>
     *
     * @param mapObj {@code searchPubTransPathT} 응답의 {@code info.mapObj} 값 (prefix 없이)
     * @return ODsay 응답 raw JSON 문자열
     */
    public String loadLane(String mapObj) {
        log.debug("ODsay loadLane 호출: mapObj={}", mapObj);
        try {
            String body = restClient.get()
                    .uri("/loadLane?mapObject=0:0@{mapObj}&apiKey={apiKey}",
                            mapObj, properties.getApiKey())
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                throw new ExternalApiException(SOURCE, ExternalApiException.Type.SERVER_ERROR,
                        null, "ODsay loadLane 응답 본문이 비어있음", null);
            }
            log.debug("ODsay loadLane 응답 수신: {} bytes", body.length());
            return body;
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            ExternalApiException.Type type = status.is4xxClientError()
                    ? ExternalApiException.Type.CLIENT_ERROR
                    : ExternalApiException.Type.SERVER_ERROR;
            throw new ExternalApiException(SOURCE, type, status.value(),
                    "ODsay loadLane 호출 실패: HTTP " + status, null);
        } catch (ResourceAccessException e) {
            Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(e);
            ExternalApiException.Type type = rootCause instanceof SocketTimeoutException
                    ? ExternalApiException.Type.TIMEOUT
                    : ExternalApiException.Type.NETWORK;
            String causeName = rootCause != null ? rootCause.getClass().getSimpleName() : "ResourceAccessException";
            throw new ExternalApiException(SOURCE, type, null,
                    "ODsay loadLane 통신 실패 (" + causeName + ")", null);
        } catch (RestClientException e) {
            throw new ExternalApiException(SOURCE, ExternalApiException.Type.NETWORK, null,
                    "ODsay loadLane 호출 중 예외 (" + e.getClass().getSimpleName() + ")", null);
        }
    }
}
