package com.todayway.backend.map.dto;

/**
 * 명세 §4.1 — {@code GET /main} 응답.
 *
 * <p>{@code nearestSchedule} 은 미인증 / 미래 일정 없을 시 {@code null} 로 직렬화되어 명세 응답 예시의
 * {@code "nearestSchedule": null} 과 정합. {@code application.yml} 에 global non-null 설정이 없고
 * Jackson 의 디폴트가 {@code Include.ALWAYS} 라 별도 어노테이션 불필요. {@code mapCenter} 는 항상 채워진다.
 */
public record MainResponse(
        NearestScheduleDto nearestSchedule,
        Coordinate mapCenter
) {
}
