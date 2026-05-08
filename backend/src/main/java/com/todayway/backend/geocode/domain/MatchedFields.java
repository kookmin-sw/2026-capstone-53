package com.todayway.backend.geocode.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 명세 §8.1 — 매칭된 지오코딩 결과의 도메인 표현. {@code GeocodeCache} factory / refresh 인자 +
 * {@link com.todayway.backend.geocode.service.KakaoLocalToGeocodeMapper} 의 변환 결과 가 모두 본
 * record 를 사용해 9-positional-arg 의 silent argument-swap 위험 (lat/lng 또는 name/address) 차단.
 *
 * <p>{@code lat/lng} 는 compact constructor 에서 non-null 강제 — 매치된 결과인데 좌표가 없으면 spec
 * 위반이므로 type-level 에서 invariant 표현 (caller 의 null 체크에만 의존하지 않게). {@code name},
 * {@code address}, {@code placeId} 는 nullable 유지 (Kakao 응답이 일부 필드를 비울 수 있음).
 *
 * <p>{@code lat/lng} 는 {@code BigDecimal} — DB DECIMAL(10,7) 정밀도 보존.
 */
public record MatchedFields(String name, String address, BigDecimal lat, BigDecimal lng, String placeId) {

    public MatchedFields {
        Objects.requireNonNull(lat, "lat must not be null");
        Objects.requireNonNull(lng, "lng must not be null");
    }
}
