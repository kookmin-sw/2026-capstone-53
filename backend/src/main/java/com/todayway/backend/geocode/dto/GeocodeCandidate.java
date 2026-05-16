package com.todayway.backend.geocode.dto;

import com.todayway.backend.external.kakao.dto.KakaoLocalSearchResponse;

/**
 * 명세 §8.2 v1.1.27 — {@code candidates[]} 단일 원소. §8.1 단일 응답에서 {@code matched} 만
 * 제외한 동일 필드 구성 — caller 가 후보 중 하나를 선택해 §8.1 흐름과 동일하게 schedule 저장 단계로
 * 진입한다.
 *
 * <p>{@code lat}/{@code lng} primitive {@code double} — Kakao Local 응답이 string 이지만
 * 검증 로직 ({@link com.todayway.backend.geocode.service.GeocodeService#searchCandidates}) 에서
 * NaN/null 가드를 거친 후에만 본 record 가 생성된다.
 *
 * <p>{@code provider} 는 §8.1 v1.1.4 변환표 — 응답 단계에서 도메인 ENUM 값
 * ({@code "KAKAO"}) 으로 변환 완료해 노출 (v1.1.30, 단일 §8.1 응답과 동일 정책).
 */
public record GeocodeCandidate(
        String name,
        String address,
        double lat,
        double lng,
        String placeId,
        String provider
) {
    /** §8.1 v1.1.4 변환표 적용. WALK fallback ({@code road_address_name} → {@code address_name})
     *  동일. caller 가 {@code x}/{@code y} parse 실패 / null 인 document 는 본 메서드 호출 전에
     *  skip 해야 한다 (record 가 primitive {@code double} 이라 null 가드 불가).
     *
     *  <p>v1.1.37 — {@code parseDouble} 만으로는 {@code "NaN"}/{@code "Infinity"} 가 통과. JSON 직렬화
     *  시 Jackson 이 NaN/Infinity 를 평문으로 흘려 프론트 지도 SDK 가 marker placement NaN 으로 폭주.
     *  {@link Double#isFinite} 가드 추가 — non-finite 면 {@link NumberFormatException} 으로 변환해
     *  caller 의 기존 skip 로직 ({@code GeocodeService.searchCandidates}) 에 자연 합류.
     */
    public static GeocodeCandidate from(KakaoLocalSearchResponse.Document doc, String provider) {
        return new GeocodeCandidate(
                doc.placeName(),
                preferRoadAddress(doc),
                parseFiniteCoord(doc.y(), "y"),
                parseFiniteCoord(doc.x(), "x"),
                doc.id(),
                provider
        );
    }

    private static double parseFiniteCoord(String raw, String field) {
        double v = Double.parseDouble(raw);
        if (!Double.isFinite(v)) {
            throw new NumberFormatException("non-finite " + field + ": " + raw);
        }
        return v;
    }

    private static String preferRoadAddress(KakaoLocalSearchResponse.Document doc) {
        String road = doc.roadAddressName();
        if (road != null && !road.isBlank()) {
            return road;
        }
        return doc.addressName();
    }
}
