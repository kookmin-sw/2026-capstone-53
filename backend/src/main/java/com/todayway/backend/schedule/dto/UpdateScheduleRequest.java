package com.todayway.backend.schedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * PATCH /schedules/{id} — 명세 §5.4 부분 업데이트. 모든 필드 nullable.
 *
 * <p>v1.1.40 — {@code reminderOffsetMinutes} 에 {@code @Min(0) @Max(1440)} 추가 (CreateScheduleRequest 와 일관).
 */
public record UpdateScheduleRequest(
        @Size(min = 1, max = 100) String title,

        @Valid PlaceDto origin,
        @Valid PlaceDto destination,

        OffsetDateTime userDepartureTime,
        OffsetDateTime arrivalTime,

        @Min(value = 0, message = "reminderOffsetMinutes는 0 이상이어야 합니다")
        @Max(value = 1440, message = "reminderOffsetMinutes는 1440(1일) 이하여야 합니다")
        Integer reminderOffsetMinutes,

        @Valid RoutineRuleDto routineRule
) {}
