package com.todayway.backend.push.service;

import com.todayway.backend.schedule.domain.Schedule;
import com.todayway.backend.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 명세 §9.1 — {@code @Scheduled(fixedDelay)} 트리거. 5분 누락 방지 윈도우 안의 due reminder 를
 * 모아 ID 만 추출 후 {@link PushReminderDispatcher#process} 에 위임.
 *
 * <p>per-iteration {@code try/catch} — 한 일정 처리 실패가 다른 일정 처리를 막지 않게 한다.
 * 트랜잭션 boundary 는 dispatcher 안. 본 클래스는 트랜잭션 X (read-only repository 호출만).
 *
 * <p>활성화는 {@link PushSchedulingConfig} 의 {@code @ConditionalOnProperty} 로 제어 —
 * {@code @EnableScheduling} 자체가 미적용되면 본 빈은 등록되어도 {@code @Scheduled} 트리거 X.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PushScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ScheduleRepository scheduleRepository;
    private final PushReminderDispatcher dispatcher;
    private final PushSchedulerProperties properties;

    @Scheduled(fixedDelayString = "${push.scheduler.fixed-delay-ms:30000}")
    public void dispatchDueReminders() {
        OffsetDateTime now = OffsetDateTime.now(KST);
        OffsetDateTime windowStart = now.minus(Duration.ofMinutes(properties.getWindowMinutes()));

        List<Long> scheduleIds = scheduleRepository.findDueReminders(now, windowStart)
                .stream()
                .map(Schedule::getId)
                .toList();

        if (scheduleIds.isEmpty()) {
            return;
        }
        log.info("Push reminder due: count={}", scheduleIds.size());

        for (Long id : scheduleIds) {
            try {
                dispatcher.process(id);
            } catch (Exception e) {
                // dispatcher 트랜잭션 rollback 후 다음 일정 처리 계속. 입력은 internal scheduleId 뿐이라
                // payload/endpoint leak 우려 없음 → e 를 last vararg 로 넘겨 stack trace 출력
                // (OdsayRouteService 패턴 미러 — 운영 root-cause analysis 가능).
                log.warn("Push reminder dispatch failed: scheduleId={}", id, e);
            }
        }
    }
}
