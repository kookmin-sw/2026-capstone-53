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
        // V1 schema 는 origin/destination lat/lng 를 NOT NULL 로 강제하지만 JPA field 는 BigDecimal
        // (JVM-nullable). DB 마이그레이션 / 직접 SQL 우회 등으로 null 이 흘러들어오면 NPE → 500 generic
        // 으로 떨어지므로 명시 가드 + scheduleUid 를 메시지에 박아 운영 진단 가능하게 한다.
        requireCoord(s.getOriginLat(), s.getOriginLng(), "origin", s.getScheduleUid());
        requireCoord(s.getDestinationLat(), s.getDestinationLng(), "destination", s.getScheduleUid());
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

    private static void requireCoord(java.math.BigDecimal lat, java.math.BigDecimal lng,
                                     String label, String scheduleUid) {
        if (lat == null || lng == null) {
            throw new IllegalStateException(
                    "Schedule " + scheduleUid + " has null " + label + " coordinate (data corruption)");
        }
    }
}
