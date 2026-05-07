package com.todayway.backend.geocode.domain;

import java.math.BigDecimal;

/**
 * 명세 §8.1 — 매칭된 지오코딩 결과의 도메인 표현. {@code GeocodeCache} factory / refresh 인자 +
 * {@link com.todayway.backend.geocode.service.KakaoLocalToGeocodeMapper} 의 변환 결과 가 모두 본
 * record 를 사용해 9-positional-arg 의 silent argument-swap 위험 (lat/lng 또는 name/address) 차단.
 *
 * <p>{@code lat/lng} 는 {@code BigDecimal} — DB DECIMAL(10,7) 정밀도 보존.
 */
public record MatchedFields(String name, String address, BigDecimal lat, BigDecimal lng, String placeId) {
}
