package com.todayway.backend.push.service;

import com.todayway.backend.push.domain.PushLog;
import com.todayway.backend.push.domain.PushSubscription;
import com.todayway.backend.push.domain.PushType;
import com.todayway.backend.push.repository.PushLogRepository;
import com.todayway.backend.push.repository.PushSubscriptionRepository;
import com.todayway.backend.schedule.domain.RoutineCalculator;
import com.todayway.backend.schedule.domain.RoutineType;
import com.todayway.backend.schedule.domain.Schedule;
import com.todayway.backend.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 명세 §9.1 v1.1.16 — {@link PushReminderDispatcher} 트랜잭션 분리. ODsay 11초 가능 호출과 push
 * provider IO 가 한 트랜잭션에 묶이지 않도록 read/write 두 짧은 트랜잭션을 별도 빈으로 분리한다.
 *
 * <p>Spring 의 self-call 은 proxy 를 통하지 않아 같은 빈 안의 메서드 간 호출에는 {@code @Transactional}
 * 이 적용되지 않는다. orchestrator({@link PushReminderDispatcher}) 가 본 빈을 외부에서 호출해야
 * 트랜잭션 경계가 정상 동작한다.
 *
 * <ul>
 *   <li>{@link #loadContext} (read-only) — schedule fetch + race 가드 (reminderAt 변경 / soft-delete /
 *       null reminderAt) + 회원 활성 구독 조회. detached 상태로 반환.</li>
 *   <li>{@link #persistAndAdvance} (write) — schedule reload + race 재검증 + ODsay 결과 적용 +
 *       PushLog INSERT + 410 → revoke + advance. race 재검증 실패 시 push_log 미저장 (관측성 누락
 *       trade-off — 사용자 입장에선 ODsay 호출~persist 사이 PATCH 라는 매우 드문 케이스).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PushReminderTransactional {

    private final ScheduleRepository scheduleRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final PushLogRepository pushLogRepository;
    private final RoutineCalculator routineCalculator;

    /**
     * read-only — race 가드 통과 시 schedule + 활성 구독 묶음 반환. soft-deleted / reminderAt
     * cleared / reminderAt 변경 모두 {@link Optional#empty()} 로 정상 흡수.
     */
    @Transactional(readOnly = true)
    public Optional<DispatchContext> loadContext(Long scheduleId, OffsetDateTime expectedReminderAt) {
        Schedule s = scheduleRepository.findById(scheduleId).orElse(null);
        if (s == null) {
            log.info("Push reminder skip — Schedule not visible (soft-deleted between scan and dispatch?): scheduleId={}",
                    scheduleId);
            return Optional.empty();
        }
        if (s.getReminderAt() == null) {
            log.debug("Push reminder skip — reminderAt cleared (race or already processed): scheduleId={}",
                    scheduleId);
            return Optional.empty();
        }
        if (!s.getReminderAt().equals(expectedReminderAt)) {
            log.info("Push reminder skip — reminderAt changed between scan and dispatch (PATCH race?): scheduleId={}",
                    scheduleId);
            return Optional.empty();
        }
        List<PushSubscription> subs = subscriptionRepository.findActiveByMemberId(s.getMemberId());
        return Optional.of(new DispatchContext(s, subs));
    }

    /**
     * write — 짧은 트랜잭션 안에서 schedule reload + ODsay 갱신값 반영 + PushLog INSERT + revoke +
     * advance. ODsay 호출 후 race 가 발생한 경우 (사용자 PATCH 로 reminderAt 재계산) 본 트랜잭션은
     * 모든 mutate 를 skip — 이미 발송된 push 의 push_log 는 누락되지만 advance 가 옛 schedule 기준으로
     * reminderAt 을 덮어쓰는 silent corruption 차단이 우선.
     */
    @Transactional
    public void persistAndAdvance(Long scheduleId,
                                  OffsetDateTime expectedReminderAt,
                                  RouteRefreshSnapshot snap,
                                  List<SendOutcome> outcomes) {
        Schedule s = scheduleRepository.findById(scheduleId).orElse(null);
        if (s == null || s.getReminderAt() == null
                || !s.getReminderAt().equals(expectedReminderAt)) {
            log.warn("Push reminder persist skip — schedule mutated during ODsay/dispatch (PATCH or DELETE race): scheduleId={}",
                    scheduleId);
            return;
        }

        if (snap.refreshOk()) {
            s.updateRouteInfo(snap.estimatedDurationMinutes(),
                    snap.recommendedDepartureTime(),
                    snap.routeSummaryJson(),
                    snap.calculatedAt());
        }

        for (SendOutcome o : outcomes) {
            pushLogRepository.save(PushLog.record(
                    o.subscriptionId(), s.getId(), PushType.REMINDER,
                    o.result().status(), o.result().httpStatus(), o.payloadJson()));
            if (o.result().isExpired()) {
                subscriptionRepository.findById(o.subscriptionId())
                        .ifPresent(sub -> {
                            log.info("Push subscription auto-revoked due to 410 Gone: subscriptionUid={}",
                                    sub.getSubscriptionUid());
                            sub.revoke();
                        });
            }
        }

        advanceOrTerminate(s);
    }

    private void advanceOrTerminate(Schedule s) {
        RoutineType type = s.getRoutineType();
        if (type == null || type == RoutineType.ONCE) {
            s.clearReminderAt();
            return;
        }
        OffsetDateTime nextArrival = routineCalculator.calculateNextOccurrence(s);
        if (nextArrival == null) {
            log.warn("Routine next occurrence not found — terminating reminder: scheduleId={}, type={}",
                    s.getId(), type);
            s.clearReminderAt();
            return;
        }
        s.advanceToNextOccurrence(nextArrival);
    }
}
