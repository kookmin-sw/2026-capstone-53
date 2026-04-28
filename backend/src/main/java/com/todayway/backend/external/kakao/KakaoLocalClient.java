package com.todayway.backend.external.kakao;

import com.todayway.backend.external.ExternalApiException;
import com.todayway.backend.external.kakao.dto.KakaoLocalSearchResponse;
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
 * 카카오 로컬 키워드 검색 API 클라이언트.
 * 명세 §8.1: /geocode 엔드포인트의 외부 호출자.
 */
@Component
public class KakaoLocalClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoLocalClient.class);
    private static final ExternalApiException.Source SOURCE = ExternalApiException.Source.KAKAO_LOCAL;

    private final RestClient restClient;

    @Autowired
    public KakaoLocalClient(KakaoLocalProperties properties) {
        this(RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(createRequestFactory(properties.getTimeoutSeconds()))
                .defaultHeader("Authorization", "KakaoAK " + properties.getApiKey())
                .build());
    }

    /** 테스트용. MockRestServiceServer로 모킹된 RestClient 주입을 위해 패키지 접근 허용.
     *  Spring 빈 생성자 후보에서 제외하기 위해 public 생성자에 @Autowired 명시함. */
    KakaoLocalClient(RestClient restClient) {
        this.restClient = restClient;
    }

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
     * 키워드로 장소 검색.
     *
     * @param query 주소 또는 장소명 (예: "국민대학교")
     * @return 검색 결과 (matched 여부는 documents.isEmpty()로 판정)
     */
    public KakaoLocalSearchResponse searchKeyword(String query) {
        log.debug("Kakao Local 호출: query={}", query);
        try {
            KakaoLocalSearchResponse res = restClient.get()
                    .uri(uri -> uri.path("/search/keyword.json")
                            .queryParam("query", query)
                            .build())
                    .retrieve()
                    .body(KakaoLocalSearchResponse.class);

            // Kakao가 빈 응답을 줄 때 후속 호출자의 NPE 방지. (OdsayClient와 일관)
            if (res == null) {
                throw new ExternalApiException(SOURCE, ExternalApiException.Type.SERVER_ERROR,
                        null, "Kakao Local 응답 본문이 비어있음", null);
            }
            log.debug("Kakao Local 응답: {}건", res.documents() != null ? res.documents().size() : 0);
            return res;
        } catch (RestClientResponseException e) {
            // 보안: cause는 응답 본문 일부 포함 가능 → 보존 X.
            HttpStatusCode status = e.getStatusCode();
            ExternalApiException.Type type = status.is4xxClientError()
                    ? ExternalApiException.Type.CLIENT_ERROR
                    : ExternalApiException.Type.SERVER_ERROR;
            throw new ExternalApiException(SOURCE, type, status.value(),
                    "Kakao Local 호출 실패: HTTP " + status, null);
        } catch (ResourceAccessException e) {
            // cause chain 끝까지 가서 timeout 판정 (factory 교체 시 안전).
            Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(e);
            ExternalApiException.Type type = rootCause instanceof SocketTimeoutException
                    ? ExternalApiException.Type.TIMEOUT
                    : ExternalApiException.Type.NETWORK;
            // 보안: ODsay와 동일 패턴. cause는 URL을 메시지에 들고 있어 보존 시 stack trace 우회 누출 위험.
            String causeName = rootCause != null ? rootCause.getClass().getSimpleName() : "ResourceAccessException";
            throw new ExternalApiException(SOURCE, type, null, "Kakao Local 통신 실패 (" + causeName + ")", null);
        } catch (RestClientException e) {
            throw new ExternalApiException(SOURCE, ExternalApiException.Type.NETWORK, null,
                    "Kakao Local 호출 중 예외 (" + e.getClass().getSimpleName() + ")", null);
        }
    }
}
