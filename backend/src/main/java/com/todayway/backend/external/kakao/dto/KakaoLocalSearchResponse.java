package com.todayway.backend.external.kakao.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 카카오 로컬 키워드 검색 API 응답 (https://dapi.kakao.com/v2/local/search/keyword.json).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoLocalSearchResponse(
        Meta meta,
        List<Document> documents
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
            @JsonProperty("total_count") int totalCount,
            @JsonProperty("pageable_count") int pageableCount,
            @JsonProperty("is_end") boolean isEnd
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
            String id,
            @JsonProperty("place_name") String placeName,
            @JsonProperty("address_name") String addressName,
            @JsonProperty("road_address_name") String roadAddressName,
            @JsonProperty("category_group_code") String categoryGroupCode,
            String x,
            String y
    ) {}
}
