package com.todayway.backend.map.dto;

import com.todayway.backend.common.web.IdPrefixes;
import com.todayway.backend.schedule.domain.Schedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(NearestScheduleDto.class);

    /**
     * Schedule → DTO 변환. V1 schema 는 origin/destination lat/lng 를 NOT NULL 로 강제하지만 JPA field 는
     * BigDecimal (JVM-nullable). DB 마이그레이션 / 직접 SQL 우회 등으로 null 이 흘러들어오면 NPE → 500
     * 누출.
     *
     * <p>v1.1.34 — 기존엔 {@code IllegalStateException} 으로 throw 해 {@code handleUnknown} 이 500 으로
     * 떨어졌다. corrupted schedule 이 1건이라도 있으면 그 회원의 {@code /main} 이 영구 500 으로 죽어
     * 첫 화면 진입조차 불가하던 결함. 본 변경으로 null 반환 + WARN 신호로 격하 — 명세 §4.1 의
     * "{@code nearestSchedule=null} 직렬화" graceful 경로와 정합. 운영 진단 가능성은 WARN 로그의
     * {@code scheduleUid} 로 보존.
     */
    public static NearestScheduleDto from(Schedule s) {
        boolean originNull = hasNullCoord(s.getOriginLat(), s.getOriginLng());
        boolean destinationNull = hasNullCoord(s.getDestinationLat(), s.getDestinationLng());
        if (originNull || destinationNull) {
            // v1.1.38 — side 표기 추가. 4 케이스 (originLat / originLng / destinationLat /
            // destinationLng) 가 같은 WARN 메시지로 묻혀 운영 진단 시 DB 재조회 없이 root cause
            // 분리가 안 되던 결함. PII 없이 정보량만 증가 — alert 매칭과 root cause 둘 다 가능.
            String side = (originNull && destinationNull) ? "ORIGIN+DESTINATION"
                    : originNull ? "ORIGIN" : "DESTINATION";
            log.warn("nearest schedule skip — corrupted coordinate detected. side={}, scheduleUid={}",
                    side, s.getScheduleUid());
            return null;
        }
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

    private static boolean hasNullCoord(java.math.BigDecimal lat, java.math.BigDecimal lng) {
        return lat == null || lng == null;
    }
}
