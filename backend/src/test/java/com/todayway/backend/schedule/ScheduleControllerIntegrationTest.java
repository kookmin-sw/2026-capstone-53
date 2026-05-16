package com.todayway.backend.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todayway.backend.auth.dto.SignupRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ScheduleControllerIntegrationTest {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("routine_commute");

    @DynamicPropertySource
    static void mysqlProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQtYmFzZTY0LXBhZGRlZC0zMmJ5dGVzLWxvbmc9PQ==");
        // 본 테스트가 schedule + reminderAt row 를 만들고 30초 윈도우 안에서 다음 케이스로
        // 이어지면 PushScheduler 가 활성화 시 같은 row 를 잡아 race · 실 IO 발생 위험.
        registry.add("push.scheduler.enabled", () -> "false");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ScheduleRepository scheduleRepository;

    @MockitoBean RouteService routeService;

    @Test
    void create_happyPath_whenRouteCalculated_returnsCalculated() throws Exception {
        // ODsay 호출 성공 시뮬레이션 — refreshRouteSync에서 schedule 필드 갱신 + true 반환.
        // departureAdvice는 Schedule.updateRouteInfo가 내부에서 자동 계산 (claude.ai PR #10 P2 후).
        when(routeService.refreshRouteSync(any(Schedule.class))).thenAnswer(inv -> {
            Schedule s = inv.getArgument(0);
            s.updateRouteInfo(
                    35,
                    s.getArrivalTime().minusMinutes(35),
                    "{\"path\":[]}",
                    OffsetDateTime.now(KST)
            );
            return true;
        });

        String accessToken = signupAndGetToken("schhappy01", "스케줄해피");
        String body = createScheduleBody(arrivalIn(60));

        mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scheduleId").exists())
                .andExpect(jsonPath("$.data.routeStatus").value("CALCULATED"))
                .andExpect(jsonPath("$.data.estimatedDurationMinutes").value(35))
                .andExpect(jsonPath("$.data.recommendedDepartureTime").exists())
                .andExpect(jsonPath("$.data.departureAdvice").value("LATER"));

        verify(routeService, times(1)).refreshRouteSync(any(Schedule.class));
    }

    @Test
    void create_whenODsayDegradation_returnsPendingRetry() throws Exception {
        // ODsay 실패 시뮬레이션 — false 반환 + schedule 미수정 (NoOpRouteService 동작과 동일)
        when(routeService.refreshRouteSync(any(Schedule.class))).thenReturn(false);

        String accessToken = signupAndGetToken("schdegrade01", "그레이스풀");
        String body = createScheduleBody(arrivalIn(60));

        mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.routeStatus").value("PENDING_RETRY"))
                .andExpect(jsonPath("$.data.estimatedDurationMinutes").doesNotExist())
                .andExpect(jsonPath("$.data.routeCalculatedAt").doesNotExist());
    }

    @Test
    void list_returnsCursorPaginated() throws Exception {
        when(routeService.refreshRouteSync(any(Schedule.class))).thenReturn(false);

        String accessToken = signupAndGetToken("schlist01", "리스트");
        // 3건 등록 (서로 다른 도착 시각)
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/v1/schedules")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createScheduleBody(arrivalIn(60 + i * 60))))
                    .andExpect(status().isCreated());
        }

        // 첫 페이지 limit=2 → 2개 + nextCursor 존재
        String firstPage = mockMvc.perform(get("/api/v1/schedules?limit=2")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.nextCursor").exists())
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        String nextCursor = objectMapper.readTree(firstPage).path("data").path("nextCursor").asText();

        // 다음 페이지 → 1개 + nextCursor null
        mockMvc.perform(get("/api/v1/schedules?limit=2&cursor=" + nextCursor)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }

    @Test
    void list_keysetHandlesAsymmetricArrivalAndId_doesNotDropSchedules() throws Exception {
        // 이슈 #15 회귀 가드 — arrival ASC 순서와 id 단조 증가가 어긋날 때
        // keyset 술어가 id 단독이면 일정 영구 누락. (arrivalTime, id) 합성 술어로 fix.
        when(routeService.refreshRouteSync(any(Schedule.class))).thenReturn(false);

        String accessToken = signupAndGetToken("schasym01", "비대칭");

        // A 먼저 등록 (arrival = NOW+10일 → id 더 작음)
        mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createScheduleBody(arrivalIn(60 * 24 * 10))))
                .andExpect(status().isCreated());
        // B 나중 등록 (arrival = NOW+5일 → id 더 크지만 arrival 더 이름)
        mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createScheduleBody(arrivalIn(60 * 24 * 5))))
                .andExpect(status().isCreated());

        // 1페이지 (limit=1) → arrival 더 이른 B 가 먼저 + nextCursor 발급
        String page1 = mockMvc.perform(get("/api/v1/schedules?limit=1")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        String firstId = objectMapper.readTree(page1).path("data").path("items").get(0).path("scheduleId").asText();
        String nextCursor = objectMapper.readTree(page1).path("data").path("nextCursor").asText();

        // 2페이지 → 남은 A 반환. id-only 술어였다면 firstId(=B, id 더 큼) 보다 큰 id 가 없어 빈 결과 → A 영구 누락.
        String page2 = mockMvc.perform(get("/api/v1/schedules?limit=1&cursor=" + nextCursor)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andReturn().getResponse().getContentAsString();
        String secondId = objectMapper.readTree(page2).path("data").path("items").get(0).path("scheduleId").asText();

        assertThat(secondId).isNotEqualTo(firstId);
    }

    @Test
    void list_whenInvalidCursorFormat_returns400() throws Exception {
        // v1 cursor (id:N base64) 또는 변조된 cursor → VALIDATION_ERROR.
        // 명세 §1.5 opaque 정책상 클라이언트는 1페이지부터 재요청해야 함.
        String accessToken = signupAndGetToken("schinval01", "잘못된");

        // v1 cursor 시뮬레이션: Base64URL("id:1")
        String legacyCursor = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("id:1".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/v1/schedules?limit=10&cursor=" + legacyCursor)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void get_whenNotOwn_returns403Forbidden() throws Exception {
        when(routeService.refreshRouteSync(any(Schedule.class))).thenReturn(false);

        // 회원 A가 schedule 생성
        String tokenA = signupAndGetToken("ownerA01", "주인A");
        String createResp = mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createScheduleBody(arrivalIn(60))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String scheduleId = objectMapper.readTree(createResp).path("data").path("scheduleId").asText();

        // 회원 B가 A의 schedule 조회 시도 → 403
        String tokenB = signupAndGetToken("strangerB01", "타인B");
        mockMvc.perform(get("/api/v1/schedules/" + scheduleId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN_RESOURCE"));
    }

    @Test
    void delete_softDeletes_thenGetReturns404() throws Exception {
        when(routeService.refreshRouteSync(any(Schedule.class))).thenReturn(false);

        String accessToken = signupAndGetToken("schdel01", "삭제");
        String createResp = mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createScheduleBody(arrivalIn(60))))
                .andReturn().getResponse().getContentAsString();
        String scheduleId = objectMapper.readTree(createResp).path("data").path("scheduleId").asText();

        mockMvc.perform(delete("/api/v1/schedules/" + scheduleId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // 동일 scheduleId 재조회 → 404 (소프트 삭제)
        mockMvc.perform(get("/api/v1/schedules/" + scheduleId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SCHEDULE_NOT_FOUND"));
    }

    @Test
    void update_whenArrivalChanged_triggersODsayRefresh() throws Exception {
        when(routeService.refreshRouteSync(any(Schedule.class))).thenReturn(false);

        String accessToken = signupAndGetToken("schupd01", "업데이트");
        String createResp = mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createScheduleBody(arrivalIn(60))))
                .andReturn().getResponse().getContentAsString();
        String scheduleId = objectMapper.readTree(createResp).path("data").path("scheduleId").asText();

        // arrivalTime 변경 → ODsay 재호출 (refreshRouteSync 2번째 호출)
        OffsetDateTime newArrival = OffsetDateTime.now(KST).plusMinutes(120);
        mockMvc.perform(patch("/api/v1/schedules/" + scheduleId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arrivalTime\":\"" + newArrival + "\"}"))
                .andExpect(status().isOk());

        verify(routeService, times(2)).refreshRouteSync(any(Schedule.class));  // create 1 + update 1
    }

    @Test
    void update_whenOnlyUserDepartureTimeChanged_doesNotCallRouteRefresh() throws Exception {
        when(routeService.refreshRouteSync(any(Schedule.class))).thenReturn(false);

        String accessToken = signupAndGetToken("schupd02", "출발만");
        String createResp = mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createScheduleBody(arrivalIn(60))))
                .andReturn().getResponse().getContentAsString();
        String scheduleId = objectMapper.readTree(createResp).path("data").path("scheduleId").asText();

        // userDepartureTime만 변경 → ODsay 재호출 X (명세 §5.4 비고)
        OffsetDateTime newDepart = OffsetDateTime.now(KST).plusMinutes(30);
        mockMvc.perform(patch("/api/v1/schedules/" + scheduleId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userDepartureTime\":\"" + newDepart + "\"}"))
                .andExpect(status().isOk());

        verify(routeService, times(1)).refreshRouteSync(any(Schedule.class));  // create 1번만
    }

    /**
     * v1.1.33 §5.1 — origin/destination 의 lat ±90 / lng ±180 범위 이탈 입력은 fail-fast
     * {@code 400 VALIDATION_ERROR}. PlaceDto {@code @DecimalMin/@DecimalMax} 가드. 외부
     * (ODsay/TMAP) 호출 단계로 새지 않도록 routeService 가 호출되지 않음도 검증.
     */
    @Test
    void create_whenCoordOutOfRange_returns400_VALIDATION_ERROR() throws Exception {
        String accessToken = signupAndGetToken("schcoord01", "좌표검증");

        OffsetDateTime arrival = OffsetDateTime.now(KST).plusMinutes(60);
        OffsetDateTime depart = arrival.minusMinutes(30);

        // origin 또는 destination 한쪽이 범위 이탈한 케이스 4종 — 모두 400.
        record BadCoord(String label, double oLat, double oLng, double dLat, double dLng) {}
        BadCoord[] cases = {
                new BadCoord("origin lat > 90", 91.0, 127.0, 37.6, 127.0),
                new BadCoord("origin lat < -90", -91.0, 127.0, 37.6, 127.0),
                new BadCoord("destination lng > 180", 37.6, 127.0, 37.6, 181.0),
                new BadCoord("destination lng < -180", 37.6, 127.0, 37.6, -181.0),
        };

        for (BadCoord c : cases) {
            String body = """
                    {
                      "title": "범위이탈-%s",
                      "origin": {"name":"출발","lat":%s,"lng":%s},
                      "destination": {"name":"도착","lat":%s,"lng":%s},
                      "userDepartureTime": "%s",
                      "arrivalTime": "%s"
                    }
                    """.formatted(c.label(), c.oLat(), c.oLng(), c.dLat(), c.dLng(), depart, arrival);

            mockMvc.perform(post("/api/v1/schedules")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        verify(routeService, never()).refreshRouteSync(any(Schedule.class));
    }

    @Test
    void create_whenArrivalBeforeDeparture_returns400() throws Exception {
        String accessToken = signupAndGetToken("schval01", "검증");

        OffsetDateTime depart = OffsetDateTime.now(KST).plusMinutes(60);
        OffsetDateTime arrival = depart.minusMinutes(10);  // arrival < depart → 400
        String body = """
                {
                  "title": "잘못된시간",
                  "origin": {"name":"출발","lat":37.66,"lng":127.01},
                  "destination": {"name":"도착","lat":37.61,"lng":126.99},
                  "userDepartureTime": "%s",
                  "arrivalTime": "%s"
                }
                """.formatted(depart, arrival);

        mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verify(routeService, never()).refreshRouteSync(any(Schedule.class));
    }

    // ───── helpers ─────

    private String signupAndGetToken(String loginId, String nickname) throws Exception {
        SignupRequest req = new SignupRequest(loginId, "P@ssw0rd!", nickname);
        String resp = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(resp).path("data");
        return node.path("accessToken").asText();
    }

    private static OffsetDateTime arrivalIn(long minutes) {
        return OffsetDateTime.now(KST).plusMinutes(minutes);
    }

    private static String createScheduleBody(OffsetDateTime arrival) {
        OffsetDateTime depart = arrival.minusMinutes(30);
        return """
                {
                  "title": "테스트일정",
                  "origin": {"name":"우이동","lat":37.6612,"lng":127.0124},
                  "destination": {"name":"국민대","lat":37.6103,"lng":126.9969},
                  "userDepartureTime": "%s",
                  "arrivalTime": "%s",
                  "reminderOffsetMinutes": 5
                }
                """.formatted(depart, arrival);
    }

    // ───── v1.1.40 슬랙 follow-up 회귀 가드 ─────

    /**
     * v1.1.40 T1 — 목록 응답에 {@code routineRule} 등 풀필드 포함 (캘린더 반복 일정 표시 버그 #2 직접 원인 회귀 가드).
     */
    @Test
    void list_response_includes_routineRule_fullFields() throws Exception {
        when(routeService.refreshRouteSync(any(Schedule.class))).thenReturn(false);
        String accessToken = signupAndGetToken("schfull01", "풀필드");

        OffsetDateTime arrival = arrivalIn(60);
        OffsetDateTime depart = arrival.minusMinutes(30);
        String body = """
                {
                  "title": "주간 반복",
                  "origin": {"name":"우이동","lat":37.6612,"lng":127.0124},
                  "destination": {"name":"국민대","lat":37.6103,"lng":126.9969},
                  "userDepartureTime": "%s",
                  "arrivalTime": "%s",
                  "reminderOffsetMinutes": 5,
                  "routineRule": {"type":"WEEKLY","daysOfWeek":["MON","TUE","WED","THU","FRI"]}
                }
                """.formatted(depart, arrival);
        mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/schedules?limit=10")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].routineRule.type").value("WEEKLY"))
                .andExpect(jsonPath("$.data.items[0].routineRule.daysOfWeek[0]").value("MON"))
                .andExpect(jsonPath("$.data.items[0].userDepartureTime").exists())
                .andExpect(jsonPath("$.data.items[0].reminderOffsetMinutes").value(5))
                .andExpect(jsonPath("$.data.items[0].departureAdviceReliable").exists());
    }

    /**
     * v1.1.40 R1 + R3 — {@code reminderOffsetMinutes} {@code @Min(0) @Max(1440)} 범위 위반 → 400.
     */
    @Test
    void create_reminderOffsetMinutes_outOfRange_returns400_VALIDATION_ERROR() throws Exception {
        String accessToken = signupAndGetToken("schoff01", "오프셋");
        OffsetDateTime arrival = arrivalIn(60);
        OffsetDateTime depart = arrival.minusMinutes(30);

        int[] invalidOffsets = {-1, -30, 1441, 10000};
        for (int bad : invalidOffsets) {
            String body = """
                    {
                      "title": "범위위반",
                      "origin": {"name":"우이동","lat":37.6612,"lng":127.0124},
                      "destination": {"name":"국민대","lat":37.6103,"lng":126.9969},
                      "userDepartureTime": "%s",
                      "arrivalTime": "%s",
                      "reminderOffsetMinutes": %d
                    }
                    """.formatted(depart, arrival, bad);
            mockMvc.perform(post("/api/v1/schedules")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    /**
     * v1.1.40 T5 — {@code userDepartureTime} 미입력 등록 정상 처리 (BE 자동 채움 흐름).
     * ODsay 실패 (PENDING_RETRY) 케이스라 자동 채움도 X 하지만 등록 자체는 통과해야.
     */
    @Test
    void create_userDepartureTime_미입력_등록_통과() throws Exception {
        when(routeService.refreshRouteSync(any(Schedule.class))).thenReturn(false);
        String accessToken = signupAndGetToken("schoptdep01", "옵셔널출발");

        OffsetDateTime arrival = arrivalIn(60);
        String body = """
                {
                  "title": "출발시각 미입력",
                  "origin": {"name":"우이동","lat":37.6612,"lng":127.0124},
                  "destination": {"name":"국민대","lat":37.6103,"lng":126.9969},
                  "arrivalTime": "%s",
                  "reminderOffsetMinutes": 5
                }
                """.formatted(arrival);
        mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                // recommendedDepartureTime 이 null (ODsay 실패) 이라 자동 채움도 X → autoFilled=false → reliable=true
                .andExpect(jsonPath("$.data.departureAdviceReliable").value(true));
    }
}
