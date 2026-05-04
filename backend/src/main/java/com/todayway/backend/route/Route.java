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
 * @param transferCount 잠정 정의: {@code subwayTransitCount + busTransitCount} (명세 §6.1 매핑표).
 *                      합산 의미가 *환승 횟수*인지 *이용 노선 수*인지는 PR #11 P2 #3 (황찬우)
 *                      명세팀 답변 대기 — 답 수령 후 정의 또는 산식 갱신 필요.
 */
public record Route(
        int totalDurationMinutes,
        int totalDistanceMeters,
        int totalWalkMeters,
        int transferCount,
        int payment,
        List<RouteSegment> segments
) {}
