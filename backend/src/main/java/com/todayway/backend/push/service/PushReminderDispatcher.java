package com.todayway.backend.push.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todayway.backend.common.web.IdPrefixes;
import com.todayway.backend.push.domain.PushSubscription;
import com.todayway.backend.push.sender.PushSendResult;
import com.todayway.backend.push.sender.PushSender;
import com.todayway.backend.route.RouteService;
import com.todayway.backend.schedule.domain.Schedule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 한 일정의 알림 발송 orchestrator. 명세 §9.1 v1.1.16 트랜잭션 분리 패턴:
 * <ol>
 *   <li>{@link PushReminderTransactional#loadContext} (read tx) — race 가드 + activeSubs.</li>
 *   <li>본 클래스 (트랜잭션 X) — ODsay 재호출 (최대 2회 + 1초 sleep, 최악 11초). 실패 시 폴백 모드.</li>
 *   <li>본 클래스 (트랜잭션 X) — sub 별 페이로드 빌드 + push provider IO. 명세 §9.1 v1.1.14 의 sub 별
 *       {@code data.subscriptionId} 차별화.</li>
 *   <li>{@link PushReminderTransactional#persistAndAdvance} (write tx) — schedule reload + race
 *       재검증 + ODsay 결과 적용 + PushLog INSERT + 410 revoke + ONCE/루틴 종결/advance.</li>
 * </ol>
 *
 * <p>{@link PushScheduler} 가 per-iteration {@code try/catch} 로 본 메서드를 감싸서 한 일정 실패가
 * 다른 일정 처리에 전파되지 않게 한다.
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

    private final PushReminderTransactional transactional;
    private final RouteService routeService;
    private final PushSender pushSender;
    private final PushSchedulerProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 단일 일정의 알림 발송 orchestrator. 트랜잭션 X — ODsay/Push provider IO 가 한 트랜잭션에 묶이지
     * 않도록 짧은 read/write 트랜잭션 두 번을 {@link PushReminderTransactional} 빈에 위임.
     *
     * <p>{@code expectedReminderAt} 은 scan 시점에 {@link PushScheduler} 가 캡처한 값 — race 가드.
     */
    public void process(Long scheduleId, OffsetDateTime expectedReminderAt) {
        Optional<DispatchContext> ctxOpt = transactional.loadContext(scheduleId, expectedReminderAt);
        if (ctxOpt.isEmpty()) {
            return;
        }
        DispatchContext ctx = ctxOpt.get();

        // 트랜잭션 밖 — ODsay (최악 11초 블로킹). detached entity 에 mutate 후 결과를 snapshot 으로 캡처.
        RouteRefreshSnapshot snap = refreshOdsayWithRetry(ctx.schedule());

        // 트랜잭션 밖 — sub 별 발송 IO. payload 의 data.subscriptionId 가 sub 마다 다르므로 sub 단위 빌드.
        List<PushSubscription> activeSubs = ctx.activeSubs();
        if (activeSubs.isEmpty()) {
            log.info("Push reminder no active subscription: scheduleId={}, memberId={}",
                    ctx.schedule().getId(), ctx.schedule().getMemberId());
        }
        List<SendOutcome> outcomes = new ArrayList<>(activeSubs.size());
        for (PushSubscription sub : activeSubs) {
            String payloadJson = buildPayloadJson(ctx.schedule(), sub, snap);
            PushSendResult result = pushSender.send(sub, payloadJson);
            outcomes.add(new SendOutcome(sub.getId(), result, payloadJson));
        }

        // 짧은 write 트랜잭션 — schedule reload + race 재검증 + 결과 persist + advance.
        transactional.persistAndAdvance(scheduleId, expectedReminderAt, snap, outcomes);
    }

    private RouteRefreshSnapshot refreshOdsayWithRetry(Schedule s) {
        int max = properties.getOdsayMaxAttempts();
        for (int attempt = 1; attempt <= max; attempt++) {
            if (attempt > 1) {
                try {
                    Thread.sleep(properties.getOdsayRetryIntervalMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Push reminder ODsay retry interrupted: scheduleId={}", s.getId());
                    return RouteRefreshSnapshot.failed();
                }
            }
            try {
                if (routeService.refreshRouteSync(s)) {
                    // refreshRouteSync 가 detached entity 를 mutate 한 결과를 snapshot 으로 캡처 —
                    // write 트랜잭션이 reload 한 entity 에 다시 setter 호출하기 위함.
                    return new RouteRefreshSnapshot(true,
                            s.getEstimatedDurationMinutes(),
                            s.getRecommendedDepartureTime(),
                            s.getRouteSummaryJson(),
                            s.getRouteCalculatedAt());
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
        return RouteRefreshSnapshot.failed();
    }

    private String buildPayloadJson(Schedule s, PushSubscription sub, RouteRefreshSnapshot snap) {
        String externalScheduleId = IdPrefixes.SCHEDULE + s.getScheduleUid();
        String externalSubscriptionId = IdPrefixes.SUBSCRIPTION + sub.getSubscriptionUid();

        // refreshOk 시 snapshot 의 갱신값, 실패 시 detached schedule 의 기존 값 (마지막 성공 스냅샷).
        OffsetDateTime recDeparture = snap.refreshOk() ? snap.recommendedDepartureTime() : s.getRecommendedDepartureTime();
        Integer duration = snap.refreshOk() ? snap.estimatedDurationMinutes() : s.getEstimatedDurationMinutes();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scheduleId", externalScheduleId);
        // 명세 §9.1 v1.1.14 — 한 회원 다중 구독 시 SW 가 어느 device 의 push 인지 식별하기 위한 키.
        data.put("subscriptionId", externalSubscriptionId);
        data.put("type", PUSH_TYPE_REMINDER);
        data.put("url", SCHEDULE_URL_PREFIX + externalScheduleId);
        if (recDeparture != null) {
            data.put("recommendedDepartureTime", recDeparture.format(ISO_OFFSET_SECONDS));
        }
        if (duration != null) {
            data.put("estimatedDurationMinutes", duration);
        }
        data.put("fallback", !snap.refreshOk());
        if (!snap.refreshOk()) {
            data.put("fallbackReason", FALLBACK_REASON_ODSAY);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", s.getTitle());
        payload.put("body", buildBody(s, recDeparture, duration));
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
    private static String buildBody(Schedule s, OffsetDateTime recDeparture, Integer duration) {
        Integer offsetMin = s.getReminderOffsetMinutes();
        String hhmm = recDeparture != null ? recDeparture.atZoneSameInstant(KST).toLocalTime().format(HHMM) : "?";
        String durationStr = duration != null ? duration + "분" : "계산 중";
        String offsetStr = offsetMin != null ? offsetMin + "분 뒤" : "곧";
        return String.format("%s 출발하세요 (%s, 예상 소요시간 %s)", offsetStr, hhmm, durationStr);
    }
}
