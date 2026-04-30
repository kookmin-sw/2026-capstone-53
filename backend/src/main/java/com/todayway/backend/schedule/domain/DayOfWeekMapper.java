package com.todayway.backend.schedule.domain;

import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;

import java.time.DayOfWeek;
import java.util.Map;

/**
 * "MON" 같은 짧은 표기 ↔ DayOfWeek 변환.
 * DayOfWeek.MONDAY.toString()은 "MONDAY"라 직접 매핑 X — 정적 맵 필요.
 * 명세 §11.2 RoutineRule.daysOfWeek 정합.
 */
public final class DayOfWeekMapper {

    private static final Map<String, DayOfWeek> SHORT_TO_DAY = Map.of(
            "MON", DayOfWeek.MONDAY,
            "TUE", DayOfWeek.TUESDAY,
            "WED", DayOfWeek.WEDNESDAY,
            "THU", DayOfWeek.THURSDAY,
            "FRI", DayOfWeek.FRIDAY,
            "SAT", DayOfWeek.SATURDAY,
            "SUN", DayOfWeek.SUNDAY
    );

    private static final Map<DayOfWeek, String> DAY_TO_SHORT = Map.of(
            DayOfWeek.MONDAY, "MON",
            DayOfWeek.TUESDAY, "TUE",
            DayOfWeek.WEDNESDAY, "WED",
            DayOfWeek.THURSDAY, "THU",
            DayOfWeek.FRIDAY, "FRI",
            DayOfWeek.SATURDAY, "SAT",
            DayOfWeek.SUNDAY, "SUN"
    );

    private DayOfWeekMapper() {}

    public static DayOfWeek fromShortCode(String code) {
        DayOfWeek d = SHORT_TO_DAY.get(code);
        if (d == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return d;
    }

    public static String toShortCode(DayOfWeek day) {
        return DAY_TO_SHORT.get(day);
    }
}
