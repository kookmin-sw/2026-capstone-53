package com.todayway.backend.geocode.dto;

import java.util.List;

/**
 * 명세 §8.2 v1.1.27 — {@code POST /geocode/search} 응답.
 *
 * <p>{@code candidates} 는 항상 non-null, non-empty — caller
 * ({@link com.todayway.backend.geocode.service.GeocodeService#searchCandidates}) 가
 * 빈 결과 / valid row 0건을 ErrorCode 로 변환해 throw. 본 record 도달 시점에 1건 이상 보장.
 */
public record GeocodeSearchResponse(
        List<GeocodeCandidate> candidates
) {
}
