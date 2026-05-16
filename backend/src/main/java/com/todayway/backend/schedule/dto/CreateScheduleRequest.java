package com.todayway.backend.schedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * POST /schedules — 명세 §5.1 Request Body.
 *
 * <p>v1.1.40 — {@code reminderOffsetMinutes} 에 {@code @Min(0) @Max(1440)} 추가 (슬랙 FE 채팅).
 * 음수는 reminder_at 이 출발시각 이후가 되는 UX 위반 + dispatcher 폴링 윈도우 silent 누락 위험.
 * 1440 분 (24시간) 상한은 일반 calendar 앱 표준 — 본 알림은 출발 알림이며 일정 사전 알림과 구분됨.
 */
public record CreateScheduleRequest(
        @NotBlank @Size(min = 1, max = 100) String title,

        @NotNull @Valid PlaceDto origin,
        @NotNull @Valid PlaceDto destination,

        OffsetDateTime userDepartureTime,
        @NotNull OffsetDateTime arrivalTime,

        @Min(value = 0, message = "reminderOffsetMinutes는 0 이상이어야 합니다")
        @Max(value = 1440, message = "reminderOffsetMinutes는 1440(1일) 이하여야 합니다")
        Integer reminderOffsetMinutes,

        @Valid RoutineRuleDto routineRule
) {}
