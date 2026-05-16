package com.todayway.backend.schedule.domain;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Set;

/**
 * 루틴 일정의 다음 occurrence 계산기. 이상진 Step 7 PushScheduler가 알림 발송 후 호출.
 * 명세 §9.2 / BACKEND_CONTEXT §9.3 의사코드 정합.
 *
 *  - ONCE: null
 *  - DAILY: current + 1일
 *  - WEEKLY: 다음 daysOfWeek 도래 (1~7일 탐색, 없으면 null) — Asia/Seoul (KST) 기준 요일
 *  - CUSTOM: current + intervalDays
 *
 * <p><b>WEEKLY KST 기준 요일 평가 (이슈 #36, v1.1.25)</b>: {@link OffsetDateTime#getDayOfWeek()}
 * 는 OffsetDateTime 의 displayed offset 기준 요일을 반환한다. {@code schedule.arrival_time}
 * (DATETIME(3)) 영속화 후 Hibernate 가 OffsetDateTime 을 UTC offset 으로 reconstruct 하면
 * KST 기준 요일과 다를 수 있음 (예: 5/12 01:24 KST = 5/11 16:24 UTC, 화요일 KST 인데 월요일 UTC).
 * 명세는 사용자 입력 요일 (KST) 정합이라 KST zone 으로 변환 후 요일 비교한다.
 */
@Component
public class RoutineCalculator {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public OffsetDateTime calculateNextOccurrence(Schedule s) {
        OffsetDateTime current = s.getArrivalTime();
        RoutineType type = s.getRoutineType();

        if (type == null || type == RoutineType.ONCE) return null;

        OffsetDateTime next = switch (type) {
            case DAILY -> current.plusDays(1);
            case WEEKLY -> nextWeeklyOccurrence(current, s.getDaysOfWeekSet());
            case CUSTOM -> nextCustomOccurrence(current, s.getRoutineIntervalDays());
            case ONCE -> null;
        };

        // v1.1.40 T4-Q3 — endDate 가드. 다음 occurrence 가 endDate 보다 미래면 null 반환
        // → §9.2 advance 종료 → caller 가 reminder_at NULL 로 dormant 처리 ("자동 삭제 X" 슬랙 #2 정합).
        // endDate KST 기준 — schedule.arrival_time 의 KST 변환 후 LocalDate 비교 (v1.1.25 패턴 정합).
        if (next != null && s.getRoutineEndDate() != null) {
            java.time.LocalDate nextKstDate = next.atZoneSameInstant(KST).toLocalDate();
            if (nextKstDate.isAfter(s.getRoutineEndDate())) {
                return null;
            }
        }
        return next;
    }

    private OffsetDateTime nextWeeklyOccurrence(OffsetDateTime current, Set<DayOfWeek> days) {
        if (days.isEmpty()) return null;
        for (int delta = 1; delta <= 7; delta++) {
            OffsetDateTime cand = current.plusDays(delta);
            if (days.contains(cand.atZoneSameInstant(KST).getDayOfWeek())) return cand;
        }
        return null;
    }

    private OffsetDateTime nextCustomOccurrence(OffsetDateTime current, Integer intervalDays) {
        if (intervalDays == null || intervalDays <= 0) return null;
        return current.plusDays(intervalDays);
    }
}
