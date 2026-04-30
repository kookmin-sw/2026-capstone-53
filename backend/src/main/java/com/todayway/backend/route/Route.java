package com.todayway.backend.route;

import java.util.List;

/**
 * 단일 경로. 명세 §11.4 정합.
 *
 * <p>v1.1.3 — {@code pathType} 필드 제거됨. 경로 모드 조합(지하철만/버스만/혼합)은
 * {@code segments[].mode}로 프론트가 파생. 외부 API 의존성 격리.
 *
 * <p>모든 필드 {@code int} (not null primitive) — 명세상 number 필수.
 * ODsay 응답에 missing field가 있으면 mapper의 {@code .asInt()}가 0을 반환 (graceful).
 *
 * @param transferCount {@code subwayTransitCount + busTransitCount} 합산 (명세 §6.1 매핑표)
 */
public record Route(
        int totalDurationMinutes,
        int totalDistanceMeters,
        int totalWalkMeters,
        int transferCount,
        int payment,
        List<RouteSegment> segments
) {}
