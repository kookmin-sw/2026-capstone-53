package com.todayway.backend.push.service;

import com.todayway.backend.push.domain.PushSubscription;
import com.todayway.backend.schedule.domain.Schedule;

import java.util.List;

/**
 * {@link PushReminderTransactional#loadContext} 의 read 트랜잭션 결과 — race 가드 통과한
 * {@link Schedule} 과 회원의 활성 구독 목록을 묶어 트랜잭션 밖 orchestrator
 * ({@link PushReminderDispatcher#process}) 에 detached 상태로 넘긴다.
 *
 * <p>본 record 의 {@link #schedule()} 은 detached — getter 호출만 안전 (mutate 은 무의미).
 * ODsay 재호출이 entity 를 mutate 하는 경우 {@link RouteRefreshSnapshot} 으로 결과를 캡처해서
 * write 트랜잭션({@link PushReminderTransactional#persistAndAdvance}) 의 reload entity 에 다시
 * 적용한다 (명세 §9.1 v1.1.16 — ODsay 11초 가능 호출을 트랜잭션 밖으로 빼는 분리 패턴).
 */
public record DispatchContext(Schedule schedule, List<PushSubscription> activeSubs) {
}
