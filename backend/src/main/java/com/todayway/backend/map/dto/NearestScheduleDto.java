package com.todayway.backend.map.dto;

import com.todayway.backend.common.web.IdPrefixes;
import com.todayway.backend.schedule.domain.Schedule;

import java.time.OffsetDateTime;

/**
 * 명세 §4.1 {@code data.nearestSchedule} 응답. 시간상 가장 가까운 미래 일정 한 건.
 * 미인증 또는 일정 없을 시 응답에서 {@code null}.
 *
 * <p>{@code hasCalculatedRoute} 는 명세 §4.1 비고대로 {@code route_summary_json IS NOT NULL} 여부.
 */
public record NearestScheduleDto(
        String scheduleId,
        String title,
        OffsetDateTime arrivalTime,
        MainPlaceDto origin,
        MainPlaceDto destination,
        boolean hasCalculatedRoute,
        OffsetDateTime recommendedDepartureTime,
        OffsetDateTime reminderAt
) {

    public static NearestScheduleDto from(Schedule s) {
        return new NearestScheduleDto(
                IdPrefixes.SCHEDULE + s.getScheduleUid(),
                s.getTitle(),
                s.getArrivalTime(),
                new MainPlaceDto(s.getOriginName(),
                        s.getOriginLat().doubleValue(), s.getOriginLng().doubleValue()),
                new MainPlaceDto(s.getDestinationName(),
                        s.getDestinationLat().doubleValue(), s.getDestinationLng().doubleValue()),
                s.getRouteSummaryJson() != null,
                s.getRecommendedDepartureTime(),
                s.getReminderAt()
        );
    }
}
