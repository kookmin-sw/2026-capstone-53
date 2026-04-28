package com.todayway.backend.external.odsay;

import com.todayway.backend.external.ExternalApiException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Duration;

/**
 * ODsay 대중교통 길찾기 API 클라이언트.
 * 명세 §5.1: 응답을 raw JSON 그대로 schedule.route_summary_json에 저장한다.
 */
@Component
public class OdsayClient {

    private static final String SOURCE = "ODSAY";

    private final OdsayProperties properties;
    private final RestClient restClient;

    public OdsayClient(OdsayProperties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
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
        try {
            // URI 템플릿 변수 확장으로 query value를 strict encoding (apiKey에 '+', '/' 포함되어 필요).
            String body = restClient.get()
                    .uri("/searchPubTransPathT?SX={sx}&SY={sy}&EX={ex}&EY={ey}&apiKey={apiKey}",
                            sx, sy, ex, ey, properties.getApiKey())
                    .retrieve()
                    .body(String.class);

            // ODsay가 빈 응답을 줄 때 후속 JSON 파싱 단계의 NPE 방지.
            if (body == null || body.isBlank()) {
                throw new ExternalApiException(SOURCE, ExternalApiException.Type.API_FAILED,
                        null, "ODsay 응답 본문이 비어있음", null);
            }
            return body;
        } catch (RestClientResponseException e) {
            // 보안: cause로 보존하지 않음. e는 응답 본문 일부를 메시지에 담을 수 있어 우회 누출 통로.
            // 진단에 필요한 정보(httpStatus, type, source)는 ExternalApiException 필드에 담음.
            throw new ExternalApiException(SOURCE, ExternalApiException.Type.API_FAILED,
                    e.getStatusCode().value(), "ODsay 호출 실패: HTTP " + e.getStatusCode(), null);
        } catch (ResourceAccessException e) {
            // cause chain 끝까지 가서 timeout 판정 (SimpleClientHttpRequestFactory는 SocketTimeoutException으로
            // connect/read timeout 모두 던지지만, 향후 factory 교체 시 안전하도록 root cause 검사).
            Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(e);
            ExternalApiException.Type type = rootCause instanceof SocketTimeoutException
                    ? ExternalApiException.Type.TIMEOUT
                    : ExternalApiException.Type.NETWORK;
            // 보안: e.getMessage()는 URL+apiKey를 통째로 포함하고, cause로 보존하면 stack trace에서 우회 누출.
            // 메시지엔 cause 클래스명만 + cause는 null로 끊음. 진단 정보는 type/source 필드로 충분.
            String causeName = rootCause != null ? rootCause.getClass().getSimpleName() : "ResourceAccessException";
            throw new ExternalApiException(SOURCE, type, null, "ODsay 통신 실패 (" + causeName + ")", null);
        } catch (RestClientException e) {
            // 보안: 동일 사유로 cause 보존 X.
            throw new ExternalApiException(SOURCE, ExternalApiException.Type.NETWORK, null,
                    "ODsay 호출 중 예외 (" + e.getClass().getSimpleName() + ")", null);
        }
    }
}
