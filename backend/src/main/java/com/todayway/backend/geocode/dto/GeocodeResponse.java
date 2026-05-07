package com.todayway.backend.geocode.dto;

import com.todayway.backend.geocode.domain.GeocodeCache;

/**
 * 명세 §8.1 — {@code POST /geocode} 응답.
 *
 * <p>service 흐름상 미스(documents 빈 배열) 는 404 GEOCODE_NO_MATCH 로 던지므로 본 record 는
 * 항상 매치된 결과 ({@code matched=true}) 만 표현. {@code matched} 필드 자체는 명세 §8.1 응답 예시에
 * 박혀있으니 그대로 노출.
 *
 * <p>{@code provider} 는 명세 §8.1 v1.1.4 변환표대로 {@code "KAKAO_LOCAL"} 그대로 노출 — schedule
 * 저장 시점에 {@code "KAKAO"} 로 변환하는 책임은 schedule 도메인 (frontend 또는 ScheduleService).
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

    public static GeocodeResponse from(GeocodeCache c) {
        return new GeocodeResponse(
                c.isMatched(),
                c.getName(),
                c.getAddress(),
                c.getLat() != null ? c.getLat().doubleValue() : 0.0,
                c.getLng() != null ? c.getLng().doubleValue() : 0.0,
                c.getPlaceId(),
                c.getProvider().name()
        );
    }
}
