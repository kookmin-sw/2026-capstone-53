package com.todayway.backend.route;

import java.time.OffsetDateTime;

/**
 * 임시 시그니처 — Step 6 (이상진)에서 명세 §11.4 Route 도메인 record로 좁힘.
 * 본 시점엔 ScheduleService 컴파일을 위한 placeholder.
 */
public record RouteResponse(
        String scheduleId,
        Object route,
        OffsetDateTime calculatedAt
) {}
