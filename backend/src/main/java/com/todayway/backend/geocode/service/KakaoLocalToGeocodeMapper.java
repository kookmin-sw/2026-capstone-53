package com.todayway.backend.geocode.service;

import com.todayway.backend.external.kakao.dto.KakaoLocalSearchResponse;

import java.math.BigDecimal;

/**
 * 명세 §8.1 v1.1.4 — Kakao Local 응답 → 캐시 entity / 명세 응답 필드 변환 규칙.
 *
 * <p>매핑표:
 * <ul>
 *   <li>{@code name} ← {@code documents[0].place_name}</li>
 *   <li>{@code address} ← {@code documents[0].road_address_name} (빈값이면 {@code address_name} fallback)</li>
 *   <li>{@code lat} ← {@code Double.parseDouble(documents[0].y)} (Kakao 는 string 반환)</li>
 *   <li>{@code lng} ← {@code Double.parseDouble(documents[0].x)}</li>
 *   <li>{@code placeId} ← {@code documents[0].id}</li>
 * </ul>
 *
 * <p>{@code matched} / {@code provider} / cache 라이프사이클 결정은 caller 책임 (GeocodeService).
 */
final class KakaoLocalToGeocodeMapper {

    private KakaoLocalToGeocodeMapper() {
    }

    /**
     * Kakao document → 캐시 INSERT/refresh 에 필요한 매칭 결과 record. lat/lng 는 {@code BigDecimal}
     * (DB DECIMAL(10,7) 정밀도 보존).
     */
    static MatchedFields toMatchedFields(KakaoLocalSearchResponse.Document doc) {
        return new MatchedFields(
                doc.placeName(),
                preferRoadAddress(doc),
                new BigDecimal(doc.y()),
                new BigDecimal(doc.x()),
                doc.id()
        );
    }

    private static String preferRoadAddress(KakaoLocalSearchResponse.Document doc) {
        String road = doc.roadAddressName();
        if (road != null && !road.isBlank()) {
            return road;
        }
        return doc.addressName();
    }

    record MatchedFields(String name, String address, BigDecimal lat, BigDecimal lng, String placeId) {
    }
}
