package com.todayway.backend.geocode.service;

import com.todayway.backend.external.kakao.dto.KakaoLocalSearchResponse;
import com.todayway.backend.geocode.domain.MatchedFields;

import java.math.BigDecimal;

/**
 * 명세 §8.1 v1.1.4 — Kakao Local 응답 → {@link MatchedFields} 변환 규칙.
 *
 * <p>매핑표:
 * <ul>
 *   <li>{@code name} ← {@code documents[0].place_name}</li>
 *   <li>{@code address} ← {@code documents[0].road_address_name} (빈값이면 {@code address_name} fallback)</li>
 *   <li>{@code lat} ← {@code BigDecimal(documents[0].y)} (Kakao 는 string 반환, DB DECIMAL(10,7) 정밀도 보존)</li>
 *   <li>{@code lng} ← {@code BigDecimal(documents[0].x)}</li>
 *   <li>{@code placeId} ← {@code documents[0].id}</li>
 * </ul>
 *
 * <p>{@code matched} / {@code provider} / cache 라이프사이클 결정은 caller 책임 (GeocodeService).
 */
final class KakaoLocalToGeocodeMapper {

    private KakaoLocalToGeocodeMapper() {
    }

    /**
     * @throws NumberFormatException Kakao 응답의 {@code x}/{@code y} 가 numeric 으로 파싱 불가
     *                               (외부 응답 형식 위반). caller {@link GeocodeService#geocode}
     *                               가 catch 후 명세 §8.1 매핑표 502 (EXTERNAL_ROUTE_API_FAILED)
     *                               으로 변환. implicit contract 명시 — caller 변경 시 catch 누락이
     *                               silent 500 으로 떨어지지 않도록 컴파일러 도움 받기.
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
}
