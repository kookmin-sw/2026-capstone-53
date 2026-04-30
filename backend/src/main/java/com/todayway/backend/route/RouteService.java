package com.todayway.backend.route;

import com.todayway.backend.schedule.domain.Schedule;

/**
 * ODsay 호출 + 응답 매핑 + DB 저장 책임의 인터페이스.
 *
 * Step 5 (황찬우)에서 인터페이스만 정의. 구현체는 Step 6 (이상진)에서 OdsayRouteService로 작성.
 * 이상진 인계: ExternalApiException은 RuntimeException 상속 (BusinessException 아님) →
 * 호출자(본 인터페이스 구현체)에서 catch + BusinessException 변환 책임 (명세 §1.6 502/503/504 정합).
 */
public interface RouteService {

    /**
     * Schedule 등록/수정 시 호출. ODsay 호출 → schedule 엔티티의 경로 관련 필드 갱신.
     * 실패 시 graceful degradation — Schedule은 그대로 두고 false 반환.
     *
     * @return ODsay 호출 성공 + Schedule 필드 갱신 완료 여부
     */
    boolean refreshRouteSync(Schedule schedule);

    /**
     * 캐시 hit/miss 처리 후 단일 Route DTO 반환. 명세 §6.1 GET /schedules/{id}/route용.
     */
    RouteResponse getRoute(Schedule schedule, boolean forceRefresh);
}
