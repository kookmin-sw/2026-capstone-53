package com.todayway.backend.push.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todayway.backend.common.web.IdPrefixes;
import com.todayway.backend.push.domain.PushLog;
import com.todayway.backend.push.domain.PushSubscription;
import com.todayway.backend.push.domain.PushType;
import com.todayway.backend.push.repository.PushLogRepository;
import com.todayway.backend.push.repository.PushSubscriptionRepository;
import com.todayway.backend.push.sender.PushSendResult;
import com.todayway.backend.push.sender.PushSender;
import com.todayway.backend.route.RouteService;
import com.todayway.backend.schedule.domain.RoutineCalculator;
import com.todayway.backend.schedule.domain.RoutineType;
import com.todayway.backend.schedule.domain.Schedule;
import com.todayway.backend.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 한 일정의 알림 발송 처리 (트랜잭션 boundary). 명세 §9.1 흐름 정합:
 * <ol>
 *   <li>ODsay 재조회 — 총 {@link PushSchedulerProperties#getOdsayMaxAttempts()} 회 시도 (첫 호출 포함), 실패 시 폴백 모드.</li>
 *   <li>페이로드 빌드 — 명세 §9.1 형식. 폴백 시 {@code data.fallback=true} + {@code fallbackReason}.</li>
 *   <li>회원 활성 구독 모두에 발송 + {@link PushLog} 기록. 410 Gone → {@link PushSubscription#revoke()}.</li>
 *   <li>다음 occurrence — ONCE/null → {@link Schedule#clearReminderAt()},
 *       루틴 → {@link RoutineCalculator}로 next, 없으면 종결.</li>
 * </ol>
 *
 * <p>{@link PushScheduler} 가 per-iteration {@code try/catch} 로 본 메서드를 감싸서 한 일정 실패가
 * 다른 일정 처리에 전파되지 않게 한다. 트랜잭션 boundary는 본 클래스 — {@link PushScheduler} 는 X.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PushReminderDispatcher {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("H:mm");
    /** 명세 §9.1 페이로드 예시 ("2026-04-21T08:25:00+09:00") 정합 — seconds=0 도 명시 출력. */
    private static final DateTimeFormatter ISO_OFFSET_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final String FALLBACK_REASON_ODSAY = "EXTERNAL_ROUTE_API_FAILED";
    private static final String SCHEDULE_URL_PREFIX = "/schedules/";
    private static final String PUSH_TYPE_REMINDER = "REMINDER";

    private final ScheduleRepository scheduleRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final PushLogRepository pushLogRepository;
    private final RouteService routeService;
    private final RoutineCalculator routineCalculator;
    private final PushSender pushSender;
    private final PushSchedulerProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 단일 일정의 알림 발송. 본 트랜잭션 안에서 ODsay 재호출 + 발송 + 다음 occurrence 갱신을 모두 수행.
     *
     * <p>{@link Schedule} 은 다시 fetch — {@link PushScheduler} 가 트랜잭션 밖에서 ID만 추출했기 때문.
     * fetch 시 {@code @SQLRestriction("deleted_at IS NULL")} 로 soft-deleted 자동 제외.
     * fetch 와 트랜잭션 시작 사이에 일정이 삭제·갱신되었을 수 있어 {@code reminderAt} 재확인.
     */
    @Transactional
    public void process(Long scheduleId) {
        Schedule s = scheduleRepository.findById(scheduleId).orElse(null);
        if (s == null) {
            // soft-delete 만 사용하는 codebase 에서 row 가 사라지는 것은 비정상 — 명시적 WARN.
            log.warn("Push reminder skip — Schedule disappeared between scan and dispatch: scheduleId={}",
                    scheduleId);
            return;
        }
        if (s.getReminderAt() == null) {
            // 동시 PATCH/dispatch 또는 직전 advance 로 reminderAt 이 이미 정리됨 — 정상 race.
            log.debug("Push reminder skip — reminderAt cleared (race or already processed): scheduleId={}",
                    scheduleId);
            return;
        }

        boolean refreshOk = refreshOdsayWithRetry(s);
        String payloadJson = buildPayloadJson(s, refreshOk);

        List<PushSubscription> activeSubs = subscriptionRepository.findActiveByMemberId(s.getMemberId());
        if (activeSubs.isEmpty()) {
            log.info("Push reminder no active subscription: scheduleId={}, memberId={}",
                    s.getId(), s.getMemberId());
        }
        for (PushSubscription sub : activeSubs) {
            PushSendResult result = pushSender.send(sub, payloadJson);
            pushLogRepository.save(PushLog.record(
                    sub.getId(), s.getId(), PushType.REMINDER,
                    result.status(), result.httpStatus(), payloadJson));
            if (result.isExpired()) {
                // 410 Gone 자동 cleanup — 운영 관측성을 위해 dispatcher 레벨에서도 INFO 로깅
                // (PushSender 에서 같은 이벤트를 INFO 로 남기지만 cause 가 다른 호출자 추적에 유용).
                log.info("Push subscription auto-revoked due to 410 Gone: subscriptionUid={}",
                        sub.getSubscriptionUid());
                sub.revoke();
            }
        }

        advanceOrTerminate(s);
    }

    private boolean refreshOdsayWithRetry(Schedule s) {
        int max = properties.getOdsayMaxAttempts();
        for (int attempt = 1; attempt <= max; attempt++) {
            if (attempt > 1) {
                try {
                    Thread.sleep(properties.getOdsayRetryIntervalMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Push reminder ODsay retry interrupted: scheduleId={}", s.getId());
                    return false;
                }
            }
            try {
                if (routeService.refreshRouteSync(s)) {
                    return true;
                }
                log.debug("ODsay refresh returned false: scheduleId={}, attempt={}/{}",
                        s.getId(), attempt, max);
            } catch (Exception e) {
                // RouteService 가 unchecked 던져도 폴백 페이로드로 발송이 진행되도록 흡수.
                // scheduleId 만 입력이라 stack trace 노출 안전.
                log.warn("ODsay refresh threw exception: scheduleId={}, attempt={}/{}",
                        s.getId(), attempt, max, e);
            }
        }
        return false;
    }

    private String buildPayloadJson(Schedule s, boolean refreshOk) {
        String externalScheduleId = IdPrefixes.SCHEDULE + s.getScheduleUid();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scheduleId", externalScheduleId);
        data.put("type", PUSH_TYPE_REMINDER);
        data.put("url", SCHEDULE_URL_PREFIX + externalScheduleId);
        if (s.getRecommendedDepartureTime() != null) {
            data.put("recommendedDepartureTime", s.getRecommendedDepartureTime().format(ISO_OFFSET_SECONDS));
        }
        if (s.getEstimatedDurationMinutes() != null) {
            data.put("estimatedDurationMinutes", s.getEstimatedDurationMinutes());
        }
        data.put("fallback", !refreshOk);
        if (!refreshOk) {
            data.put("fallbackReason", FALLBACK_REASON_ODSAY);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", s.getTitle());
        payload.put("body", buildBody(s));
        payload.put("data", data);

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // 본 메서드가 만든 in-memory Map 만 직렬화하므로 정상 흐름엔 발생 X.
            // 발생 시 fail-fast — config 또는 ObjectMapper 손상 시그널.
            throw new IllegalStateException("Push payload 직렬화 실패", e);
        }
    }

    /** 명세 §9.1 body 예시: "5분 뒤 출발하세요 (8:25, 예상 소요시간 35분)". */
    private static String buildBody(Schedule s) {
        Integer offsetMin = s.getReminderOffsetMinutes();
        OffsetDateTime depart = s.getRecommendedDepartureTime();
        Integer duration = s.getEstimatedDurationMinutes();

        String hhmm = depart != null ? depart.atZoneSameInstant(KST).toLocalTime().format(HHMM) : "?";
        String durationStr = duration != null ? duration + "분" : "계산 중";
        String offsetStr = offsetMin != null ? offsetMin + "분 뒤" : "곧";
        return String.format("%s 출발하세요 (%s, 예상 소요시간 %s)", offsetStr, hhmm, durationStr);
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
