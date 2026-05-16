package com.todayway.backend.external.tmap;

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
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.util.Map;

/**
 * TMAP 보행자 경로안내 API 클라이언트. 명세 §6.1 v1.1.21 — WALK 구간 인도 곡선 제공자.
 *
 * <p>호출: {@code POST /tmap/routes/pedestrian?version=1&format=json}, {@code appKey} 헤더 + body 좌표.
 * 응답 GeoJSON FeatureCollection — caller (OdsayResponseMapper) 가 LineString feature 의 coordinates 를
 * 평탄화해서 {@code RouteSegment.path} 채움.
 *
 * <p>호출자 정책 (명세 §6.1 v1.1.21):
 * <ul>
 *   <li>모든 실패 (401/403/timeout/5xx/응답 형식 위반) → graceful — caller 가 catch 후 v1.1.9 의
 *       합성 직선 알고리즘으로 fallback. WALK 곡선 누락은 시각 품질 저하일 뿐이라 사용자 영향 작음.</li>
 *   <li>{@link com.todayway.backend.external.odsay.OdsayClient} 와 달리 401/403 도 503
 *       {@code EXTERNAL_AUTH_MISCONFIGURED} 로 격상하지 않는다 — TMAP 키 미설정도 정상 시작
 *       (다른 도메인 작업자 영향 X). 모든 4xx/5xx/timeout 은 mapper 의 catch 분기에서 v1.1.9
 *       합성 직선으로 흡수.</li>
 * </ul>
 */
@Component
public class TmapClient {

    private static final Logger log = LoggerFactory.getLogger(TmapClient.class);
    private static final ExternalApiException.Source SOURCE = ExternalApiException.Source.TMAP;

    private final TmapProperties properties;
    private final RestClient restClient;

    @Autowired
    public TmapClient(TmapProperties properties) {
        this(properties, RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(createRequestFactory(properties.getTimeoutSeconds()))
                .build());
    }

    /** 테스트용 — MockRestServiceServer 주입. */
    TmapClient(TmapProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    /**
     * appKey 설정 여부. caller (OdsayResponseMapper) 가 호출 전 short-circuit 으로 사용 —
     * 키 미설정 시 401 받는 비용 + warn 로그 노이즈 회피. 정상 dev 환경 (TMAP 키 없음) 에서도
     * silent fallback.
     */
    public boolean isConfigured() {
        String key = properties.getAppKey();
        return key != null && !key.isBlank();
    }

    /**
     * Apache HttpClient 5 + 커넥션 풀 기반 RequestFactory. {@link com.todayway.backend.external.odsay.OdsayClient}
     * 와 동일 패턴 — {@code RequestConfig.Builder.setConnectTimeout} 은 5.2+ 에서 deprecated 되어
     * {@code ConnectionConfig.Builder} 로 옮길 수 있으나, 두 client 동시 갱신을 위한 별 PR 백로그.
     */
    @SuppressWarnings("deprecation")
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
     * 보행자 경로 호출. 응답 raw JSON 반환. caller 가 GeoJSON 파싱 책임.
     *
     * @param startLng 출발 경도
     * @param startLat 출발 위도
     * @param endLng   도착 경도
     * @param endLat   도착 위도
     * @return TMAP 응답 raw JSON 문자열 (FeatureCollection)
     * @throws ExternalApiException 모든 호출 실패 — caller 가 graceful fallback 처리
     */
    public String routesPedestrian(double startLng, double startLat, double endLng, double endLat) {
        // defensive — caller 가 isConfigured() 검증 우회해 직접 호출 시 null appKey 가
        // RestClient.header("appKey", null) 에서 NPE 던지지 않게 ExternalApiException 으로 정상화.
        // mapper 의 catch 분기가 graceful fallback 으로 흡수.
        if (!isConfigured()) {
            throw new ExternalApiException(SOURCE, ExternalApiException.Type.CLIENT_ERROR,
                    null, "TMAP_APP_KEY 미설정", null);
        }
        // v1.1.33 — 좌표 PII 마스킹. OdsayClient 와 동일 패턴 (소수점 1자리 = ~10km 도시 정확도).
        log.debug("TMAP pedestrian: ({},{})→({},{})",
                maskCoord(startLng), maskCoord(startLat), maskCoord(endLng), maskCoord(endLat));
        Map<String, Object> body = Map.of(
                "startX", startLng, "startY", startLat,
                "endX", endLng, "endY", endLat,
                "reqCoordType", "WGS84GEO",
                "resCoordType", "WGS84GEO",
                "startName", "start",
                "endName", "end"
        );
        try {
            String resp = restClient.post()
                    .uri("/routes/pedestrian?version=1&format=json")
                    .header("appKey", properties.getAppKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            if (resp == null || resp.isBlank()) {
                throw new ExternalApiException(SOURCE, ExternalApiException.Type.SERVER_ERROR,
                        null, "TMAP 응답 본문이 비어있음", null);
            }
            log.debug("TMAP 응답 수신: {} bytes", resp.length());
            return resp;
        } catch (RestClientResponseException e) {
            // 보안: cause body 에 좌표/키 일부 포함 가능 → 보존 X.
            HttpStatusCode status = e.getStatusCode();
            ExternalApiException.Type type = status.is4xxClientError()
                    ? ExternalApiException.Type.CLIENT_ERROR
                    : ExternalApiException.Type.SERVER_ERROR;
            throw new ExternalApiException(SOURCE, type, status.value(),
                    "TMAP 호출 실패: HTTP " + status, null);
        } catch (ResourceAccessException e) {
            Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(e);
            ExternalApiException.Type type = rootCause instanceof SocketTimeoutException
                    ? ExternalApiException.Type.TIMEOUT
                    : ExternalApiException.Type.NETWORK;
            String causeName = rootCause != null ? rootCause.getClass().getSimpleName() : "ResourceAccessException";
            throw new ExternalApiException(SOURCE, type, null,
                    "TMAP 통신 실패 (" + causeName + ")", null);
        } catch (RestClientException e) {
            // SSL handshake / 직렬화 / 기타 RestClientException subclass 는 4xx/5xx 와 달리 응답
            // 본문이 message 에 들어갈 위험이 작음. 그러나 일관성 + 보안 보수적 정책으로 cause=null
            // 유지하고, root-cause 진단을 위해 logger 에 stack 한 번만 출력 (e 를 last vararg).
            log.warn("TMAP 호출 중 예외 — class={}", e.getClass().getSimpleName(), e);
            throw new ExternalApiException(SOURCE, ExternalApiException.Type.NETWORK, null,
                    "TMAP 호출 중 예외 (" + e.getClass().getSimpleName() + ")", null);
        }
    }

    /** v1.1.33 — 좌표 PII 마스킹 헬퍼. {@link com.todayway.backend.external.odsay.OdsayClient} 와 동일. */
    private static String maskCoord(double v) {
        return String.format("%.1f*", v);
    }
}
