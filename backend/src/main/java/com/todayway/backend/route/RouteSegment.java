package com.todayway.backend.route;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 경로 구간. 명세 §11.5 정합.
 *
 * <p>모드별 nullable 필드 가이드:
 * <ul>
 *   <li>{@link SegmentMode#WALK}: {@code lineName/lineId/stationStart/stationEnd/stationCount} 모두 {@code null}</li>
 *   <li>{@link SegmentMode#SUBWAY}: {@code lineName + lineId + stationStart + stationEnd + stationCount}</li>
 *   <li>{@link SegmentMode#BUS}: {@code lineName + lineId} (정류장명은 {@code from/to}로 노출)</li>
 * </ul>
 *
 * <p>{@code @JsonInclude(NON_NULL)} — 명세 §6.1 응답 예시상 WALK 구간에는 {@code lineName} 등의
 * 키가 아예 존재하지 않아야 한다. {@code ScheduleResponse}는 명시적 {@code null}을 표현해야 해서
 * 글로벌 {@code spring.jackson.default-property-inclusion}은 적용 불가 — 이 record에만 한정.
 *
 * @param path {@code [lng, lat]} 좌표 배열. 프론트가 그대로 polyline에 사용.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RouteSegment(
        SegmentMode mode,
        int durationMinutes,
        int distanceMeters,
        String from,
        String to,
        String lineName,
        String lineId,
        String stationStart,
        String stationEnd,
        Integer stationCount,
        List<double[]> path
) {}
