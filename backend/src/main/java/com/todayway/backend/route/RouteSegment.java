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
) {
    public RouteSegment {
        // polyline은 최소 2점 필요 — 단일 점/null/빈 path는 명세 §11.5 위반.
        // record 자체에 invariant 박아 caller(mapper/외부)와 무관하게 보장.
        if (path == null || path.size() < 2) {
            throw new IllegalArgumentException(
                    "RouteSegment.path는 2점 이상 필요 — mode=" + mode
                            + " size=" + (path == null ? "null" : path.size()));
        }
        if (mode == null) {
            throw new IllegalArgumentException("RouteSegment.mode는 null 불가");
        }
        // mode-specific nullable matrix (명세 §11.5 / §6.1 매핑표):
        //   WALK   — line*/station* 5필드 모두 null
        //   SUBWAY — lineName + lineId + stationStart + stationEnd + stationCount 모두 non-null
        //   BUS    — lineName + lineId만 non-null (station* 필드는 null — from/to와 중복)
        switch (mode) {
            case WALK -> {
                if (from != null || to != null
                        || lineName != null || lineId != null
                        || stationStart != null || stationEnd != null || stationCount != null) {
                    throw new IllegalArgumentException(
                            "WALK segment는 from/to/line*/station* 필드 모두 null이어야 함");
                }
            }
            case SUBWAY -> {
                if (lineName == null || lineId == null
                        || stationStart == null || stationEnd == null || stationCount == null) {
                    throw new IllegalArgumentException(
                            "SUBWAY segment는 lineName/lineId/stationStart/stationEnd/stationCount 모두 필수");
                }
            }
            case BUS -> {
                if (lineName == null || lineId == null) {
                    throw new IllegalArgumentException("BUS segment는 lineName/lineId 필수");
                }
                if (stationStart != null || stationEnd != null) {
                    throw new IllegalArgumentException(
                            "BUS segment는 stationStart/stationEnd null 필수 (from/to와 중복 회피)");
                }
            }
        }
    }
}
