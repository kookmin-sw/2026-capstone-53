package com.todayway.backend.schedule.dto;

import com.todayway.backend.schedule.domain.DepartureAdvice;
import com.todayway.backend.schedule.domain.Schedule;

import java.time.OffsetDateTime;

/**
 * 명세 §11.3 Schedule 응답 — 5.1/5.3/5.4 공통 형태.
 * routeStatus는 도출값 (DB 컬럼 X) — Schedule.hasCalculatedRoute() 기반.
 */
public record ScheduleResponse(
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
        OffsetDateTime createdAt
) {
    public static ScheduleResponse from(Schedule s) {
        return new ScheduleResponse(
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
                s.getCreatedAt()
        );
    }
}
