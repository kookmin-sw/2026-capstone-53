package com.todayway.backend.schedule.dto;

import com.todayway.backend.schedule.domain.DepartureAdvice;
import com.todayway.backend.schedule.domain.Schedule;

import java.time.OffsetDateTime;

/**
 * 명세 §11.3 Schedule 응답 — 5.1/5.3/5.4 공통 형태.
 * routeStatus는 도출값 (DB 컬럼 X) — Schedule.hasCalculatedRoute() 기반.
 */
public record ScheduleResponse(
        String scheduleId,
        String title,
        PlaceDto origin,
        PlaceDto destination,
        OffsetDateTime userDepartureTime,
        OffsetDateTime arrivalTime,
        Integer estimatedDurationMinutes,
        OffsetDateTime recommendedDepartureTime,
        DepartureAdvice departureAdvice,
        Integer reminderOffsetMinutes,
        OffsetDateTime reminderAt,
        RoutineRuleDto routineRule,
        String routeStatus,
        OffsetDateTime routeCalculatedAt,
        OffsetDateTime createdAt,
        /**
         * v1.1.40 T5-Q4 — {@code departureAdvice} 비교 신호의 신뢰성. {@code false} 는 BE 가
         * {@code userDepartureTime} 자동 채움 (사용자 미입력 시 recommendedDepartureTime 으로 fallback)
         * → user==recommended 라 비교 의미 X. FE 는 false 일 때 advice 회색 처리 권고. true 는 사용자
         * 명시 입력이라 EARLIER/ON_TIME/LATER 비교 신호 신뢰 가능.
         */
        boolean departureAdviceReliable,
        /**
         * v1.1.40 R4 — 등록/수정 시 {@code reminderAt} 이 NOW() 보다 과거가 되면 {@code NOW()+60s}
         * 로 clamp 했음을 표시. dispatcher 5분 폴링 윈도우 silent 누락 방지. 일반 조회에선 false.
         */
        boolean reminderClamped,
        /**
         * v1.1.40 R4-Q2 — clamp floor({@code NOW()+60s}) 가 ceiling({@code arrivalTime-1min}) 보다
         * 미래라 의미 있는 알림이 불가능한 경우 {@code reminderAt=null} 로 skip 했음을 표시.
         * 일반 조회에선 false.
         */
        boolean reminderSkipped
) {
    /** 일반 조회 — clamp/skip 메타 모두 false. {@code departureAdviceReliable} 은 엔티티 상태에서 derive. */
    public static ScheduleResponse from(Schedule s) {
        return withClampMeta(s, false, false);
    }

    /** v1.1.40 — 등록/수정 시 service 가 clamp/skip 결과를 응답에 전달. */
    public static ScheduleResponse withClampMeta(Schedule s, boolean clamped, boolean skipped) {
        return new ScheduleResponse(
                "sch_" + s.getScheduleUid(),
                s.getTitle(),
                PlaceDto.fromOrigin(s),
                PlaceDto.fromDestination(s),
                s.getUserDepartureTime(),
                s.getArrivalTime(),
                s.getEstimatedDurationMinutes(),
                s.getRecommendedDepartureTime(),
                s.getDepartureAdvice(),
                s.getReminderOffsetMinutes(),
                s.getReminderAt(),
                RoutineRuleDto.from(s),
                s.hasCalculatedRoute() ? "CALCULATED" : "PENDING_RETRY",
                s.getRouteCalculatedAt(),
                s.getCreatedAt(),
                !s.isUserDepartureTimeAutoFilled(),
                clamped,
                skipped
        );
    }
}
