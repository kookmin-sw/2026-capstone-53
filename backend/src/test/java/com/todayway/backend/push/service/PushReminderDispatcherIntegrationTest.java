package com.todayway.backend.push.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todayway.backend.auth.dto.SignupRequest;
import com.todayway.backend.push.domain.PushLog;
import com.todayway.backend.push.domain.PushStatus;
import com.todayway.backend.push.domain.PushSubscription;
import com.todayway.backend.push.repository.PushLogRepository;
import com.todayway.backend.push.repository.PushSubscriptionRepository;
import com.todayway.backend.push.sender.PushSendResult;
import com.todayway.backend.push.sender.PushSender;
import com.todayway.backend.route.RouteService;
import com.todayway.backend.schedule.domain.Schedule;
import com.todayway.backend.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 명세 §9.1 / §9.2 — PushReminderDispatcher 통합 테스트.
 *
 * <p>{@code RouteService} / {@code PushSender} 는 {@code @MockitoBean} 으로 대체 — 외부 호출 없이
 * 흐름 분기만 검증. push.scheduler.enabled=false 로 자동 트리거 차단, dispatcher 직접 호출.
 *
 * <p>회귀 가드: 정상 SENT + ONCE 종료 / ODsay 실패 폴백 / 410 EXPIRED + revoke / 루틴 advance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PushReminderDispatcherIntegrationTest {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final String SCH_PREFIX = "sch_";
    private static final String SUB_PREFIX = "sub_";

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("routine_commute");

    @DynamicPropertySource
    static void mysqlProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQtYmFzZTY0LXBhZGRlZC0zMmJ5dGVzLWxvbmc9PQ==");
        registry.add("push.scheduler.enabled", () -> "false");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PushReminderDispatcher dispatcher;
    @Autowired ScheduleRepository scheduleRepository;
    @Autowired PushSubscriptionRepository subscriptionRepository;
    @Autowired PushLogRepository pushLogRepository;

    @MockitoBean RouteService routeService;
    @MockitoBean PushSender pushSender;

    @Test
    void 정상_ODsay성공_PushSender_SENT_ONCE_일정_reminderAt_NULL() throws Exception {
        stubRouteRefreshSuccess(34);
        when(pushSender.send(any(PushSubscription.class), any(String.class)))
                .thenReturn(PushSendResult.sent(201));

        String token = signupAndGetToken("disp01", "정상발송");
        String externalSubId = subscribe(token, "https://fcm.googleapis.com/fcm/send/disp01");
        String externalScheduleId = createOnceSchedule(token);

        Long scheduleDbId = schId(externalScheduleId);
        dispatch(scheduleDbId);

        Schedule s = scheduleRepository.findById(scheduleDbId).orElseThrow();
        assertNull(s.getReminderAt(), "ONCE 일정 발송 후 reminder_at 은 NULL (재발송 방지)");
        assertNotNull(s.getRouteCalculatedAt(), "ODsay 갱신 결과 반영");

        List<PushLog> logs = pushLogRepository.findBySubscriptionId(subId(externalSubId));
        assertEquals(1, logs.size());
        assertEquals(PushStatus.SENT, logs.get(0).getStatus());
        assertEquals(201, logs.get(0).getHttpStatus());
        // 명세 §9.1 v1.1.14 — payload data 에 subscriptionId 포함 (멀티 디바이스 식별).
        JsonNode payload = objectMapper.readTree(logs.get(0).getPayloadJson());
        assertEquals(externalSubId, payload.path("data").path("subscriptionId").asText());

        Long subDbId = subId(externalSubId);
        PushSubscription sub = subscriptionRepository.findById(subDbId).orElseThrow();
        assertTrue(sub.isActive(), "정상 발송에선 구독 유지");
    }

    @Test
    void ODsay_재시도_모두_실패_폴백_페이로드_fallback_true_SENT() throws Exception {
        // 등록 시는 정상 (reminder_at 박힘). dispatch 시점에는 false.
        stubRouteRefreshSuccess(40);
        String token = signupAndGetToken("disp02", "폴백");
        String externalSubId = subscribe(token, "https://fcm.googleapis.com/fcm/send/disp02");
        String externalScheduleId = createOnceSchedule(token);

        // 등록 끝났으니 이후 dispatch 시점에는 ODsay 실패
        when(routeService.refreshRouteSync(any(Schedule.class))).thenReturn(false);
        when(pushSender.send(any(PushSubscription.class), any(String.class)))
                .thenReturn(PushSendResult.sent(201));

        Long scheduleDbId = schId(externalScheduleId);
        dispatch(scheduleDbId);

        // odsayMaxAttempts=2 — 폴백 진입 시 총 2회 호출됨 (등록 시 1회 + dispatch 시 추가 2회 = 3회 호출 누적)
        verify(routeService, times(3)).refreshRouteSync(any(Schedule.class));

        List<PushLog> logs = pushLogRepository.findBySubscriptionId(subId(externalSubId));
        assertEquals(1, logs.size());
        assertEquals(PushStatus.SENT, logs.get(0).getStatus());

        JsonNode payload = objectMapper.readTree(logs.get(0).getPayloadJson());
        assertTrue(payload.path("data").path("fallback").asBoolean(),
                "ODsay 실패 시 data.fallback=true (명세 §9.1)");
        assertEquals("EXTERNAL_ROUTE_API_FAILED",
                payload.path("data").path("fallbackReason").asText());

        // 사용 안 함 경고 차단 — externalSubId 검증 (구독 보존)
        Long subDbId = subId(externalSubId);
        assertTrue(subscriptionRepository.findById(subDbId).orElseThrow().isActive());
    }

    @Test
    void PushSender_410_Gone_응답시_push_log_EXPIRED_subscription_revoke() throws Exception {
        stubRouteRefreshSuccess(34);
        when(pushSender.send(any(PushSubscription.class), any(String.class)))
                .thenReturn(PushSendResult.expired());

        String token = signupAndGetToken("disp03", "만료");
        String externalSubId = subscribe(token, "https://fcm.googleapis.com/fcm/send/disp03");
        String externalScheduleId = createOnceSchedule(token);

        Long scheduleDbId = schId(externalScheduleId);
        dispatch(scheduleDbId);

        List<PushLog> logs = pushLogRepository.findBySubscriptionId(subId(externalSubId));
        assertEquals(1, logs.size());
        assertEquals(PushStatus.EXPIRED, logs.get(0).getStatus());
        assertEquals(410, logs.get(0).getHttpStatus());

        Long subDbId = subId(externalSubId);
        PushSubscription sub = subscriptionRepository.findById(subDbId).orElseThrow();
        assertEquals(false, sub.isActive(), "410 Gone 시 자동 revoke (명세 §12.6)");
    }

    @Test
    void DAILY_루틴_일정_advance_to_next_occurrence_reminderAt_미래_갱신() throws Exception {
        stubRouteRefreshSuccess(30);
        when(pushSender.send(any(PushSubscription.class), any(String.class)))
                .thenReturn(PushSendResult.sent(201));

        String token = signupAndGetToken("disp04", "루틴");
        subscribe(token, "https://fcm.googleapis.com/fcm/send/disp04");

        OffsetDateTime origArrival = OffsetDateTime.now(KST).plusMinutes(60);
        String externalScheduleId = createDailySchedule(token, origArrival);

        // 명세 v1.1.13 — userDepartureTime delta shift 검증을 위해 advance 전 값 캡처.
        // baseline 은 DB fetch 값으로 통일 — test variable origArrival 은 ns precision 이지만
        // DB DATETIME(3) ms 절단되어 1 초 차이가 생길 수 있음.
        Long scheduleDbId = schId(externalScheduleId);
        Schedule before = scheduleRepository.findById(scheduleDbId).orElseThrow();
        OffsetDateTime origArrivalFromDb = before.getArrivalTime();
        OffsetDateTime origUserDepart = before.getUserDepartureTime();

        dispatcher.process(scheduleDbId, before.getReminderAt());

        Schedule s = scheduleRepository.findById(scheduleDbId).orElseThrow();
        assertTrue(s.getArrivalTime().isAfter(origArrival.plusHours(23)),
                "DAILY 루틴은 다음 날로 advance (명세 §9.2)");
        assertNotNull(s.getReminderAt(), "advance 후 reminderAt 재계산 (NULL 아님)");
        assertTrue(s.getReminderAt().isAfter(OffsetDateTime.now(KST)),
                "advance 후 reminderAt 은 미래 시각");

        // 명세 v1.1.13 — userDepartureTime 도 동일 delta 만큼 shift 되어야 departureAdvice 정합 유지.
        assertTrue(s.getUserDepartureTime().isAfter(origUserDepart),
                "userDepartureTime 도 advance 시 shift 되어야 함 (v1.1.13 §9.2 보강)");
        long arrivalDeltaSec = java.time.Duration.between(origArrivalFromDb, s.getArrivalTime()).getSeconds();
        long userDepartDeltaSec = java.time.Duration.between(origUserDepart, s.getUserDepartureTime()).getSeconds();
        assertEquals(arrivalDeltaSec, userDepartDeltaSec,
                "userDepartureTime delta == arrivalTime delta — silent corruption 방지 (v1.1.13)");
    }

    // ─────────── S5 / Q1 / Q2 회귀 가드 — PR #24 review fix ───────────

    @Test
    void 멀티_디바이스_한_회원_2개_구독_중_하나_410_나머지_SENT_S5() throws Exception {
        // 명세 §9.1 — for (PushSubscription sub : activeSubs) sub 별 독립 처리.
        // 한 sub 의 410 EXPIRED 가 다른 sub 의 발송에 전파되면 안 된다.
        stubRouteRefreshSuccess(34);

        String token = signupAndGetToken("dispmulti", "멀티");
        String externalSubId1 = subscribe(token, "https://fcm.googleapis.com/fcm/send/multi-1");
        String externalSubId2 = subscribe(token, "https://fcm.googleapis.com/fcm/send/multi-2");
        String externalScheduleId = createOnceSchedule(token);

        Long subDbId1 = subId(externalSubId1);
        Long subDbId2 = subId(externalSubId2);
        when(pushSender.send(any(PushSubscription.class), any(String.class)))
                .thenAnswer(inv -> {
                    PushSubscription sub = inv.getArgument(0);
                    return sub.getId().equals(subDbId1)
                            ? PushSendResult.expired()
                            : PushSendResult.sent(201);
                });

        dispatch(schId(externalScheduleId));

        // sub-1 → EXPIRED + revoked
        List<PushLog> logs1 = pushLogRepository.findBySubscriptionId(subDbId1);
        assertEquals(1, logs1.size());
        assertEquals(PushStatus.EXPIRED, logs1.get(0).getStatus());
        assertFalse(subscriptionRepository.findById(subDbId1).orElseThrow().isActive(),
                "410 받은 sub-1 은 revoke");

        // sub-2 → SENT + 활성 유지
        List<PushLog> logs2 = pushLogRepository.findBySubscriptionId(subDbId2);
        assertEquals(1, logs2.size());
        assertEquals(PushStatus.SENT, logs2.get(0).getStatus());
        assertTrue(subscriptionRepository.findById(subDbId2).orElseThrow().isActive(),
                "정상 발송된 sub-2 는 활성 유지");

        // 명세 §9.1 v1.1.14 — payload data.subscriptionId 가 sub 마다 다름 (멀티 디바이스 식별).
        JsonNode p1 = objectMapper.readTree(logs1.get(0).getPayloadJson());
        JsonNode p2 = objectMapper.readTree(logs2.get(0).getPayloadJson());
        assertEquals(externalSubId1, p1.path("data").path("subscriptionId").asText());
        assertEquals(externalSubId2, p2.path("data").path("subscriptionId").asText());
    }

    @Test
    void PATCH_dispatch_race__reminderAt_변경시_dispatcher_skip_Q1() throws Exception {
        // 명세 §9.1 v1.1.14 — scan↔dispatch 사이 PATCH 로 reminderAt 이 변경되면 dispatcher skip.
        // 기존 reminderAt 은 새 시각으로 옮겨가 다음 폴링 사이클에 다시 잡혀 발송됨 (중복 방지).
        when(pushSender.send(any(PushSubscription.class), any(String.class)))
                .thenReturn(PushSendResult.sent(201));

        String token = signupAndGetToken("disprace", "race");
        String externalSubId = subscribe(token, "https://fcm.googleapis.com/fcm/send/race");
        String externalScheduleId = createOnceSchedule(token);
        Long scheduleDbId = schId(externalScheduleId);

        // scan 시점의 reminderAt 캡처
        OffsetDateTime expected = scheduleRepository.findById(scheduleDbId).orElseThrow().getReminderAt();

        // 사용자가 PATCH 로 arrivalTime 을 미래로 변경 → reminderAt 재계산
        OffsetDateTime newArrival = OffsetDateTime.now(KST).plusHours(3);
        String patchBody = "{\"arrivalTime\": \"" + newArrival + "\"}";
        mockMvc.perform(patch("/api/v1/schedules/" + externalScheduleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isOk());

        // dispatcher 가 옛 reminderAt 으로 호출 → race 가드가 skip
        dispatcher.process(scheduleDbId, expected);

        // pushSender 호출 0건 + PushLog 0건
        verify(pushSender, never()).send(any(PushSubscription.class), any(String.class));
        Long subDbId = subId(externalSubId);
        List<PushLog> logs = pushLogRepository.findBySubscriptionId(subDbId);
        assertEquals(0, logs.size(), "PATCH race 시 dispatcher skip — PushLog 미생성");
    }

    @Test
    void WEEKLY_루틴_advance_지정요일_userDepartureTime_delta_shift_S5() throws Exception {
        // 명세 §9.2 — WEEKLY 는 daysOfWeek 중 가장 가까운 미래 요일로 advance.
        // DAILY 만 검증하던 기존 회귀 가드의 사각지대 보강.
        stubRouteRefreshSuccess(20);
        when(pushSender.send(any(PushSubscription.class), any(String.class)))
                .thenReturn(PushSendResult.sent(201));

        String token = signupAndGetToken("dispweek", "주간");
        subscribe(token, "https://fcm.googleapis.com/fcm/send/week");

        OffsetDateTime origArrival = OffsetDateTime.now(KST).plusMinutes(60);
        String externalScheduleId = createWeeklySchedule(token, origArrival);
        Long scheduleDbId = schId(externalScheduleId);
        Schedule before = scheduleRepository.findById(scheduleDbId).orElseThrow();
        OffsetDateTime origArrivalFromDb = before.getArrivalTime();
        OffsetDateTime origUserDepart = before.getUserDepartureTime();

        dispatcher.process(scheduleDbId, before.getReminderAt());

        Schedule s = scheduleRepository.findById(scheduleDbId).orElseThrow();
        assertTrue(s.getArrivalTime().isAfter(origArrival),
                "WEEKLY advance 후 arrivalTime 은 미래");
        java.time.DayOfWeek dow = s.getArrivalTime().atZoneSameInstant(KST).getDayOfWeek();
        assertTrue(dow == java.time.DayOfWeek.MONDAY
                        || dow == java.time.DayOfWeek.WEDNESDAY
                        || dow == java.time.DayOfWeek.FRIDAY,
                "WEEKLY advance 후 routineDaysOfWeek 안의 요일이어야 함, 실제=" + dow);

        long arrivalDeltaSec = java.time.Duration.between(origArrivalFromDb, s.getArrivalTime()).getSeconds();
        long userDepartDeltaSec = java.time.Duration.between(origUserDepart, s.getUserDepartureTime()).getSeconds();
        assertEquals(arrivalDeltaSec, userDepartDeltaSec,
                "WEEKLY advance 도 userDepartureTime 동일 delta shift (v1.1.13 정합)");
    }

    // ───── helpers ─────

    /** scan↔dispatch race 가드 시그니처(Q1 v1.1.14) — 현 reminderAt 을 캡처해 dispatcher 호출. */
    private void dispatch(Long scheduleDbId) {
        OffsetDateTime expected = scheduleRepository.findById(scheduleDbId).orElseThrow().getReminderAt();
        dispatcher.process(scheduleDbId, expected);
    }

    private void stubRouteRefreshSuccess(int durationMinutes) {
        when(routeService.refreshRouteSync(any(Schedule.class))).thenAnswer(inv -> {
            Schedule s = inv.getArgument(0);
            s.updateRouteInfo(durationMinutes,
                    s.getArrivalTime().minusMinutes(durationMinutes),
                    "{\"path\":{},\"lane\":null}",
                    OffsetDateTime.now(KST));
            return true;
        });
    }

    private String signupAndGetToken(String loginId, String nickname) throws Exception {
        SignupRequest req = new SignupRequest(loginId, "P@ssw0rd!", nickname);
        String resp = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("accessToken").asText();
    }

    private String subscribe(String token, String endpoint) throws Exception {
        String body = """
                { "endpoint": "%s", "keys": { "p256dh": "BNc-test", "auth": "auth-test" } }
                """.formatted(endpoint);
        String resp = mockMvc.perform(post("/api/v1/push/subscribe")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("subscriptionId").asText();
    }

    private String createOnceSchedule(String token) throws Exception {
        OffsetDateTime arrival = OffsetDateTime.now(KST).plusMinutes(60);
        return createSchedule(token, arrival, null);
    }

    private String createDailySchedule(String token, OffsetDateTime arrival) throws Exception {
        return createSchedule(token, arrival, "\"routineRule\": { \"type\": \"DAILY\" },");
    }

    private String createWeeklySchedule(String token, OffsetDateTime arrival) throws Exception {
        return createSchedule(token, arrival,
                "\"routineRule\": { \"type\": \"WEEKLY\", \"daysOfWeek\": [\"MON\", \"WED\", \"FRI\"] },");
    }

    private String createSchedule(String token, OffsetDateTime arrival, String routineRuleJsonField)
            throws Exception {
        OffsetDateTime depart = arrival.minusMinutes(30);
        String routinePart = routineRuleJsonField != null ? routineRuleJsonField : "";
        String body = """
                {
                  "title": "통합테스트",
                  "origin": {"name":"우이동","lat":37.6612,"lng":127.0124},
                  "destination": {"name":"국민대","lat":37.6103,"lng":126.9969},
                  "userDepartureTime": "%s",
                  "arrivalTime": "%s",
                  %s
                  "reminderOffsetMinutes": 5
                }
                """.formatted(depart, arrival, routinePart);
        String resp = mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("scheduleId").asText();
    }

    private Long schId(String externalScheduleId) {
        String uid = externalScheduleId.startsWith(SCH_PREFIX)
                ? externalScheduleId.substring(SCH_PREFIX.length()) : externalScheduleId;
        return scheduleRepository.findByScheduleUid(uid).orElseThrow().getId();
    }

    private Long subId(String externalSubId) {
        String uid = externalSubId.startsWith(SUB_PREFIX)
                ? externalSubId.substring(SUB_PREFIX.length()) : externalSubId;
        return subscriptionRepository.findBySubscriptionUid(uid).orElseThrow().getId();
    }
}
