package com.todayway.backend.route;

import com.todayway.backend.common.response.ApiResponse;
import com.todayway.backend.common.web.CurrentMember;
import com.todayway.backend.common.web.IdPrefixes;
import com.todayway.backend.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 명세 §6.1 — 일정 경로 조회 (단일).
 * <p>인증 + 인가 검증은 {@link ScheduleService#getRouteForOwned} 가 담당 (단일 트랜잭션 facade).
 * 본 controller 는 HTTP 레이어 변환만 — {@link IdPrefixes#SCHEDULE} strip + {@link ApiResponse} 래핑.
 */
@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class RouteController {

    private final ScheduleService scheduleService;

    @GetMapping("/{scheduleId}/route")
    public ResponseEntity<ApiResponse<RouteResponse>> getRoute(
            @CurrentMember String memberUid,
            @PathVariable String scheduleId,
            @RequestParam(defaultValue = "false") boolean forceRefresh) {
        RouteResponse resp = scheduleService.getRouteForOwned(
                memberUid, IdPrefixes.strip(scheduleId, IdPrefixes.SCHEDULE), forceRefresh);
        return ResponseEntity.ok(ApiResponse.of(resp));
    }
}
