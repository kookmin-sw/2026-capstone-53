package com.todayway.backend.schedule.dto;

import com.todayway.backend.schedule.domain.DepartureAdvice;
import com.todayway.backend.schedule.domain.Schedule;

import java.time.OffsetDateTime;

/**
 * 명세 §5.2 일정 목록 응답 아이템. v1.1.40 — 단일 응답 ({@link ScheduleResponse}) 과 동일 필드 통일.
 *
 * <p>v1.1.40 이전엔 {@code scheduleId / title / arrivalTime / recommendedDepartureTime / origin /
 * destination / routeStatus} 7개 필드만 포함되어, 프론트가 캘린더에서 반복 일정의 {@code routineRule.daysOfWeek}
 * 를 못 받아 단발로 처리해 {@code arrivalTime} 날짜 1건만 표시하던 결함 (슬랙 #2). 단일 조회 (POST/{id})
 * 응답과 동일 필드로 통일해 프론트가 분기 없이 처리 가능.
 *
 * <p>{@code ScheduleResponse} 와 record 분리 유지 — 향후 목록 응답 경량화 필요 시 다시 분리할 수 있도록
 * 코드 trail 보존 (현재는 코드 중복 8필드, 약 5~8줄 부담).
 */
public record ScheduleListItem(
        String scheduleId,
        String title,
        PlaceDto origin,
        PlaceDto destination,
        OffsetDateTime userDepartureTime,
        OffsetDateTime arrivalTime,
        Integer estimatedDurationMinutes,
        OffsetDateTime recommendedDepartureTime,
        DepartureAdvice departureAdvice,
        Integer reminderOffsetMinutes,
        OffsetDateTime reminderAt,
        RoutineRuleDto routineRule,
        String routeStatus,
        OffsetDateTime routeCalculatedAt,
        OffsetDateTime createdAt,
        /** v1.1.40 T5-Q4 — {@link ScheduleResponse#departureAdviceReliable} 와 동일 의미. */
        boolean departureAdviceReliable
) {
    public static ScheduleListItem from(Schedule s) {
        return new ScheduleListItem(
                "sch_" + s.getScheduleUid(),
                s.getTitle(),
                PlaceDto.fromOrigin(s),
                PlaceDto.fromDestination(s),
                s.getUserDepartureTime(),
                s.getArrivalTime(),
                s.getEstimatedDurationMinutes(),
                s.getRecommendedDepartureTime(),
                s.getDepartureAdvice(),
                s.getReminderOffsetMinutes(),
                s.getReminderAt(),
                RoutineRuleDto.from(s),
                s.hasCalculatedRoute() ? "CALCULATED" : "PENDING_RETRY",
                s.getRouteCalculatedAt(),
                s.getCreatedAt(),
                !s.isUserDepartureTimeAutoFilled()
        );
    }
}
