package com.todayway.backend.schedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * POST /schedules — 명세 §5.1 Request Body.
 */
public record CreateScheduleRequest(
        @NotBlank @Size(min = 1, max = 100) String title,

        @NotNull @Valid PlaceDto origin,
        @NotNull @Valid PlaceDto destination,

        @NotNull OffsetDateTime userDepartureTime,
        @NotNull OffsetDateTime arrivalTime,

        Integer reminderOffsetMinutes,

        @Valid RoutineRuleDto routineRule
) {}
