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
 * <p><b>주의 — {@code Route.equals} / {@code RouteSegment.equals} 함정</b>: record 의 자동 equals
 * 가 {@code segments} 의 {@link RouteSegment#path} ({@code List<double[]>}) 를 deep 비교하지 못한다.
 * Java 의 {@code double[]} 는 reference equality 라 같은 좌표 sequence 라도 다른 instance 면 false.
 * 현재 caller 에서 {@code Route.equals} 호출은 0건이라 무관하지만, 캐시 동등 비교 / 테스트
 * {@code assertThat(route).isEqualTo(expected)} 등 호출 등장 시 {@code List<double[]>} →
 * {@code List<Coordinate>} 마이그레이션 필요 (Step 6 PR #11 follow-up 3번).
 *
 * @param transferCount {@code subwayTransitCount + busTransitCount} 합산 (명세 §6.1 매핑표
 *                      v1.1.20). **이용 대중교통 노선 수 (= 탑승 횟수)** — ODsay 응답 예시 (지하철
 *                      1노선 + 도보 = 1) 와 정합. 환승 횟수가 필요한 caller 는 {@code transferCount - 1}
 *                      (0 노선 케이스는 {@code Math.max(0, n-1)}) 로 파생.
 */
public record Route(
        int totalDurationMinutes,
        int totalDistanceMeters,
        int totalWalkMeters,
        int transferCount,
        int payment,
        List<RouteSegment> segments
) {}
