package com.todayway.backend.schedule.dto;

import com.todayway.backend.schedule.domain.Schedule;

import java.time.OffsetDateTime;

/**
 * 명세 §5.2 일정 목록 응답 아이템 — 페이로드 절감용 가벼운 버전.
 * route_summary_json 등 무거운 필드 제외. 상세는 §5.3에서 조회.
 */
public record ScheduleListItem(
        String scheduleId,
        String title,
        OffsetDateTime arrivalTime,
        OffsetDateTime recommendedDepartureTime,
        PlaceDto origin,
        PlaceDto destination,
        String routeStatus
) {
    public static ScheduleListItem from(Schedule s) {
        return new ScheduleListItem(
                "sch_" + s.getScheduleUid(),
                s.getTitle(),
                s.getArrivalTime(),
                s.getRecommendedDepartureTime(),
                PlaceDto.fromOrigin(s),
                PlaceDto.fromDestination(s),
                s.hasCalculatedRoute() ? "CALCULATED" : "PENDING_RETRY"
        );
    }
}
