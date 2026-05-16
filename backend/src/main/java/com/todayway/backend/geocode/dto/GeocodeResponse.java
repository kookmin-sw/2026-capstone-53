package com.todayway.backend.geocode.dto;

import com.todayway.backend.geocode.domain.GeocodeCache;
import com.todayway.backend.schedule.domain.PlaceProvider;

/**
 * 명세 §8.1 — {@code POST /geocode} 응답.
 *
 * <p>service 흐름상 미스(documents 빈 배열) 는 404 GEOCODE_NO_MATCH 로 던지므로 본 record 는
 * 항상 매치된 결과 ({@code matched=true}) 만 표현. {@code matched} 필드 자체는 명세 §8.1 응답 예시에
 * 박혀있으니 그대로 노출.
 *
 * <p>{@code provider} 는 명세 §8.1 v1.1.4 변환표 — 응답 단계에서 도메인 ENUM 값
 * ({@link PlaceProvider#KAKAO}) 으로 변환 완료해 노출 (v1.1.30). 캐시 row 의 세분 구분
 * ({@code KAKAO_LOCAL}) 은 backend 내부 식별자라 API 표면에 노출하지 않는다 — 프론트는
 * {@code Place.provider} ENUM ({@code NAVER/KAKAO/ODSAY/MANUAL}) 만 사용.
 */
public record GeocodeResponse(
        boolean matched,
        String name,
        String address,
        double lat,
        double lng,
        String placeId,
        String provider
) {

    /**
     * cache row → 응답 변환. 입력 invariant: {@code c.isMatched()} 여야 한다 — caller(GeocodeService)
     * 가 미스 row 는 GEOCODE_NO_MATCH 로 throw 하기 때문. {@code lat/lng = null} 인 매치 row 는
     * 데이터 손상 시그널이라 silent {@code 0.0} fallback 대신 {@link IllegalStateException} 으로 surface
     * (Null Island 응답 차단). {@link com.todayway.backend.map.dto.NearestScheduleDto#from} 패턴과 일관.
     */
    public static GeocodeResponse from(GeocodeCache c) {
        if (!c.isMatched()) {
            throw new IllegalStateException(
                    "GeocodeResponse.from called on miss row, queryHash=" + c.getQueryHash());
        }
        if (c.getLat() == null || c.getLng() == null) {
            throw new IllegalStateException(
                    "GeocodeCache id=" + c.getId() + " matched=true 인데 lat/lng null (data corruption)");
        }
        return new GeocodeResponse(
                true,
                c.getName(),
                c.getAddress(),
                c.getLat().doubleValue(),
                c.getLng().doubleValue(),
                c.getPlaceId(),
                PlaceProvider.KAKAO.name()
        );
    }
}
