package com.todayway.backend.schedule.domain;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * 루틴 일정의 다음 occurrence 계산기. 이상진 Step 7 PushScheduler가 알림 발송 후 호출.
 * 명세 §9.2 / BACKEND_CONTEXT §9.3 의사코드 정합.
 *
 *  - ONCE: null
 *  - DAILY: current + 1일
 *  - WEEKLY: 다음 daysOfWeek 도래 (1~7일 탐색, 없으면 null)
 *  - CUSTOM: current + intervalDays
 */
@Component
public class RoutineCalculator {

    public OffsetDateTime calculateNextOccurrence(Schedule s) {
        OffsetDateTime current = s.getArrivalTime();
        RoutineType type = s.getRoutineType();

        if (type == null || type == RoutineType.ONCE) return null;

        return switch (type) {
            case DAILY -> current.plusDays(1);
            case WEEKLY -> nextWeeklyOccurrence(current, s.getDaysOfWeekSet());
            case CUSTOM -> nextCustomOccurrence(current, s.getRoutineIntervalDays());
            case ONCE -> null;
        };
    }

    private OffsetDateTime nextWeeklyOccurrence(OffsetDateTime current, Set<DayOfWeek> days) {
        if (days.isEmpty()) return null;
        for (int delta = 1; delta <= 7; delta++) {
            OffsetDateTime cand = current.plusDays(delta);
            if (days.contains(cand.getDayOfWeek())) return cand;
        }
        return null;
    }

    private OffsetDateTime nextCustomOccurrence(OffsetDateTime current, Integer intervalDays) {
        if (intervalDays == null || intervalDays <= 0) return null;
        return current.plusDays(intervalDays);
    }
}
