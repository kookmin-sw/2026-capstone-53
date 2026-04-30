package com.todayway.backend.schedule.dto;

import com.todayway.backend.schedule.domain.RoutineType;
import com.todayway.backend.schedule.domain.Schedule;
import jakarta.validation.constraints.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * 명세 §11.2 RoutineRule 정합.
 *  - WEEKLY: daysOfWeek 사용
 *  - CUSTOM: intervalDays 사용
 *  - DAILY/ONCE: 둘 다 사용 X
 */
public record RoutineRuleDto(
        @NotNull RoutineType type,
        List<String> daysOfWeek,
        Integer intervalDays
) {
    public static RoutineRuleDto from(Schedule s) {
        if (s.getRoutineType() == null) return null;
        return new RoutineRuleDto(
                s.getRoutineType(),
                csvToList(s.getRoutineDaysOfWeek()),
                s.getRoutineIntervalDays()
        );
    }

    private static List<String> csvToList(String csv) {
        if (csv == null || csv.isBlank()) return null;
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .toList();
    }

    public String toCsv() {
        if (daysOfWeek == null || daysOfWeek.isEmpty()) return null;
        return String.join(",", daysOfWeek);
    }
}
