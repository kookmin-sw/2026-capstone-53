package com.todayway.backend.route;

import com.todayway.backend.schedule.domain.Schedule;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * {@code GET /schedules/{id}/route} 응답. 명세 §6.1 정합.
 *
 * <p>Step 6에서 placeholder({@code Object route})를 명세 §11.4 {@link Route}로 좁힘.
 *
 * @param scheduleId  외부 노출 ID — {@code "sch_" + Schedule.scheduleUid} (명세 §1.7)
 * @param route       단일 경로 (cache miss/hit/stale 모두 동일 형태) — non-null
 * @param calculatedAt ODsay 호출 시각. cache hit/stale 시엔 갱신되지 않음 — 신선도 판단 용도 (§6.1 비고).
 *                    v1.1.36 — non-null 강제. 응답이 만들어지는 모든 경로(fresh/cache hit/stale) 에서
 *                    route 데이터가 존재한다는 건 ODsay 호출이 적어도 한 번 성공했다는 뜻이므로
 *                    이 값은 본질적으로 non-null. 자료 손상 (`route_summary_json` 만 채워지고
 *                    `route_calculated_at` 은 NULL) 이 응답에 silent leak 되는 것을 boundary 에서 차단.
 */
public record RouteResponse(
        String scheduleId,
        Route route,
        OffsetDateTime calculatedAt
) {

    public RouteResponse {
        Objects.requireNonNull(scheduleId, "scheduleId");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(calculatedAt, "calculatedAt");
    }

    /**
     * Schedule 도메인 + Route 매핑 결과로 응답 생성.
     * {@code sch_} prefix 처리 위치를 DTO 정적 팩토리로 통일 (ScheduleResponse 패턴과 일관).
     */
    public static RouteResponse of(Schedule schedule, Route route) {
        return new RouteResponse(
                "sch_" + schedule.getScheduleUid(),
                route,
                schedule.getRouteCalculatedAt()
        );
    }
}
