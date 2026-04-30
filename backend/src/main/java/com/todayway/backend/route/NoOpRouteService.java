package com.todayway.backend.route;

import com.todayway.backend.schedule.domain.Schedule;
import lombok.extern.slf4j.Slf4j;

/**
 * Step 6 (이상진 OdsayRouteService) 진입 전 임시 구현체.
 * 모든 호출 false / null 반환 → ScheduleService의 graceful degradation 흐름 자체 검증 가능.
 *
 * Step 6 PR 머지 시: 이상진이 OdsayRouteService를 @Component로 등록하면
 * RouteServiceConfig의 @ConditionalOnMissingBean이 NoOp 미생성 → 자연 비활성.
 * 이후 본 클래스는 cleanup 대상 (이상진 Step 6 PR에서 삭제 권장).
 */
@Slf4j
public class NoOpRouteService implements RouteService {

    @Override
    public boolean refreshRouteSync(Schedule schedule) {
        log.info("NoOpRouteService.refreshRouteSync — OdsayRouteService 미구현 (graceful degradation, scheduleUid={})",
                schedule.getScheduleUid());
        return false;
    }

    @Override
    public RouteResponse getRoute(Schedule schedule, boolean forceRefresh) {
        log.info("NoOpRouteService.getRoute — OdsayRouteService 미구현 (scheduleUid={}, forceRefresh={})",
                schedule.getScheduleUid(), forceRefresh);
        return new RouteResponse(
                "sch_" + schedule.getScheduleUid(),
                null,
                null
        );
    }
}
