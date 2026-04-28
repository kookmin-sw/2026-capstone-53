package com.todayway.backend.external.kakao;

import com.todayway.backend.external.ExternalApiException;
import com.todayway.backend.external.kakao.dto.KakaoLocalSearchResponse;
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
 * 카카오 로컬 키워드 검색 API 클라이언트.
 * 명세 §8.1: /geocode 엔드포인트의 외부 호출자.
 */
@Component
public class KakaoLocalClient {

    private static final String SOURCE = "KAKAO_LOCAL";

    private final RestClient restClient;

    public KakaoLocalClient(KakaoLocalProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader("Authorization", "KakaoAK " + properties.getApiKey())
                .build();
    }

    /**
     * 키워드로 장소 검색.
     *
     * @param query 주소 또는 장소명 (예: "국민대학교")
     * @return 검색 결과 (matched 여부는 documents.isEmpty()로 판정)
     */
    public KakaoLocalSearchResponse searchKeyword(String query) {
        try {
            return restClient.get()
                    .uri(uri -> uri.path("/search/keyword.json")
                            .queryParam("query", query)
                            .build())
                    .retrieve()
                    .body(KakaoLocalSearchResponse.class);
        } catch (RestClientResponseException e) {
            throw new ExternalApiException(SOURCE, ExternalApiException.Type.API_FAILED,
                    e.getStatusCode().value(), "Kakao Local 호출 실패: HTTP " + e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            // cause chain 끝까지 가서 timeout 판정 (factory 교체 시 안전).
            Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(e);
            ExternalApiException.Type type = rootCause instanceof SocketTimeoutException
                    ? ExternalApiException.Type.TIMEOUT
                    : ExternalApiException.Type.NETWORK;
            throw new ExternalApiException(SOURCE, type, null, "Kakao Local 통신 실패: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw new ExternalApiException(SOURCE, ExternalApiException.Type.NETWORK, null,
                    "Kakao Local 호출 중 예외: " + e.getMessage(), e);
        }
    }
}
