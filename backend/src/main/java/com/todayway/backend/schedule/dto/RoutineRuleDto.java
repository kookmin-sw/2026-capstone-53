package com.todayway.backend.schedule.dto;

import com.todayway.backend.schedule.domain.RoutineType;
import com.todayway.backend.schedule.domain.Schedule;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * 명세 §11.2 RoutineRule 정합.
 *  - WEEKLY: daysOfWeek 사용
 *  - CUSTOM: intervalDays 사용
 *  - DAILY/ONCE: 둘 다 사용 X
 *
 * <p>v1.1.40 — {@code startDate} / {@code endDate} 추가 (슬랙 #4 + 추가사항 (4)).
 * 두 필드 모두 nullable — {@code startDate=null} 은 "등록 시점부터 시작", {@code endDate=null}
 * 은 "무한반복" (default). backward compat 보존 — 기존 일정 동작 불변.
 */
public record RoutineRuleDto(
        @NotNull RoutineType type,
        List<String> daysOfWeek,
        Integer intervalDays,
        LocalDate startDate,
        LocalDate endDate
) {
    public static RoutineRuleDto from(Schedule s) {
        if (s.getRoutineType() == null) return null;
        return new RoutineRuleDto(
                s.getRoutineType(),
                csvToList(s.getRoutineDaysOfWeek()),
                s.getRoutineIntervalDays(),
                s.getRoutineStartDate(),
                s.getRoutineEndDate()
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
