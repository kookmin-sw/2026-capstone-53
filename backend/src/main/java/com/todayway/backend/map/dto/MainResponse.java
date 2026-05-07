package com.todayway.backend.map.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 명세 §4.1 — {@code GET /main} 응답.
 *
 * <p>{@code nearestSchedule} 은 미인증 / 미래 일정 없을 시 {@code null}. Jackson 의
 * {@code @JsonInclude(NON_NULL)} 로 직렬화에서 키 자체가 사라지지 않도록 record 레벨에는 적용하지
 * 않고 그대로 둔다 — 명세 §4.1 응답 예시에 {@code nearestSchedule} 키가 항상 등장하기 때문.
 * 다만 mapCenter 는 항상 채워진다.
 */
public record MainResponse(
        @JsonInclude NearestScheduleDto nearestSchedule,
        Coordinate mapCenter
) {
}
