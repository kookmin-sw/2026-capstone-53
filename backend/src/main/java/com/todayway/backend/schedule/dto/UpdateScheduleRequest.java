package com.todayway.backend.schedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * PATCH /schedules/{id} — 명세 §5.4 부분 업데이트. 모든 필드 nullable.
 */
public record UpdateScheduleRequest(
        @Size(min = 1, max = 100) String title,

        @Valid PlaceDto origin,
        @Valid PlaceDto destination,

        OffsetDateTime userDepartureTime,
        OffsetDateTime arrivalTime,

        Integer reminderOffsetMinutes,

        @Valid RoutineRuleDto routineRule
) {}
