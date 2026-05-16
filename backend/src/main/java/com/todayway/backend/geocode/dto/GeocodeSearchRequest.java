package com.todayway.backend.geocode.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 명세 §8.2 v1.1.27 — {@code POST /geocode/search} 요청. 자동완성 후보 N건 조회.
 *
 * <p>{@code size} 는 nullable wrapper — request body 에서 누락 시 default 10 적용 (caller 가 null
 * 체크 후 fallback). primitive {@code int} 면 누락 시 자동 0 으로 fall-through 되어 {@code @Min(1)}
 * 위반 메시지가 사용자 의도 (생략 = default) 와 어긋난다.
 */
public record GeocodeSearchRequest(
        @NotBlank
        @Size(max = 255)
        String query,

        @Min(1)
        @Max(10)
        Integer size
) {
    /** 명세 §8.2 default. {@link Integer} null = 누락 = default 10 적용 (cap 과 동일 — 클라이언트가
     *  생략하면 항상 상한까지 후보 노출). */
    public static final int DEFAULT_SIZE = 10;

    public int sizeOrDefault() {
        return size != null ? size : DEFAULT_SIZE;
    }
}
