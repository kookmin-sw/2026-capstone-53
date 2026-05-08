package com.todayway.backend.geocode.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 명세 §8.1 — {@code POST /geocode} 요청. {@code query} 는 주소 또는 장소명.
 *
 * <p>{@code @Size(max=255)} 는 V1__init.sql {@code geocode_cache.query_text VARCHAR(255)} 와 동기 —
 * SQL truncation 500 회피용 1차 가드.
 */
public record GeocodeRequest(
        @NotBlank
        @Size(max = 255)
        String query
) {
}
