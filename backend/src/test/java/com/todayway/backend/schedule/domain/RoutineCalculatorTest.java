package com.todayway.backend.schedule.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RoutineCalculator 단위 테스트. BACKEND_CONTEXT §13.4 표 6 케이스.
 *
 * | current | type   | daysOfWeek    | intervalDays | expected  |
 * |---------|--------|---------------|--------------|-----------|
 * | 화 09:00 | WEEKLY | MON,FRI       | -            | 금 09:00  |
 * | 금 09:00 | WEEKLY | MON,WED,FRI   | -            | 월 09:00  |
 * | 일 09:00 | WEEKLY | MON           | -            | 월 09:00  |
 * | 월 09:00 | DAILY  | -             | -            | 화 09:00  |
 * | 월 09:00 | CUSTOM | -             | 3            | 목 09:00  |
 * | 월 09:00 | ONCE   | -             | -            | null      |
 */
class RoutineCalculatorTest {

    private final RoutineCalculator calculator = new RoutineCalculator();
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("cases")
    void calculateNextOccurrence(String name, OffsetDateTime current, RoutineType type,
                                 String daysOfWeek, Integer intervalDays, OffsetDateTime expected) {
        Schedule s = newSchedule(current, type, daysOfWeek, intervalDays);

        OffsetDateTime actual = calculator.calculateNextOccurrence(s);

        assertThat(actual).isEqualTo(expected);
    }

    static Stream<Arguments> cases() {
        // 2026-04-21 (화) 09:00 KST
        OffsetDateTime tue = OffsetDateTime.of(2026, 4, 21, 9, 0, 0, 0, KST);
        // 2026-04-24 (금)
        OffsetDateTime fri = OffsetDateTime.of(2026, 4, 24, 9, 0, 0, 0, KST);
        // 2026-04-26 (일)
        OffsetDateTime sun = OffsetDateTime.of(2026, 4, 26, 9, 0, 0, 0, KST);
        // 2026-04-27 (월)
        OffsetDateTime mon = OffsetDateTime.of(2026, 4, 27, 9, 0, 0, 0, KST);

        return Stream.of(
                Arguments.of("화→금 (WEEKLY MON,FRI)", tue, RoutineType.WEEKLY, "MON,FRI", null,
                        OffsetDateTime.of(2026, 4, 24, 9, 0, 0, 0, KST)),
                Arguments.of("금→월 (WEEKLY MON,WED,FRI)", fri, RoutineType.WEEKLY, "MON,WED,FRI", null,
                        OffsetDateTime.of(2026, 4, 27, 9, 0, 0, 0, KST)),
                Arguments.of("일→월 (WEEKLY MON)", sun, RoutineType.WEEKLY, "MON", null,
                        OffsetDateTime.of(2026, 4, 27, 9, 0, 0, 0, KST)),
                Arguments.of("월→화 (DAILY)", mon, RoutineType.DAILY, null, null,
                        OffsetDateTime.of(2026, 4, 28, 9, 0, 0, 0, KST)),
                Arguments.of("월→목 (CUSTOM 3일)", mon, RoutineType.CUSTOM, null, 3,
                        OffsetDateTime.of(2026, 4, 30, 9, 0, 0, 0, KST)),
                Arguments.of("월→null (ONCE)", mon, RoutineType.ONCE, null, null, null)
        );
    }

    @Test
    void calculateNextOccurrence_whenWeeklyDaysEmpty_returnsNull() {
        Schedule s = newSchedule(OffsetDateTime.now(KST), RoutineType.WEEKLY, "", null);
        assertThat(calculator.calculateNextOccurrence(s)).isNull();
    }

    @Test
    void calculateNextOccurrence_whenCustomIntervalNull_returnsNull() {
        Schedule s = newSchedule(OffsetDateTime.now(KST), RoutineType.CUSTOM, null, null);
        assertThat(calculator.calculateNextOccurrence(s)).isNull();
    }

    @Test
    void calculateNextOccurrence_whenTypeNull_returnsNull() {
        Schedule s = newSchedule(OffsetDateTime.now(KST), null, null, null);
        assertThat(calculator.calculateNextOccurrence(s)).isNull();
    }

    private static Schedule newSchedule(OffsetDateTime arrivalTime, RoutineType type,
                                        String daysOfWeek, Integer intervalDays) {
        OffsetDateTime depart = arrivalTime.minusMinutes(30);
        return Schedule.create(
                1L, "title",
                "출발", BigDecimal.ZERO, BigDecimal.ZERO, null, null, null,
                "도착", BigDecimal.ZERO, BigDecimal.ZERO, null, null, null,
                depart, arrivalTime, 5,
                type, daysOfWeek, intervalDays
        );
    }
}
