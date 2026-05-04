package com.todayway.backend.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todayway.backend.auth.dto.SignupRequest;
import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import com.todayway.backend.schedule.domain.Schedule;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 명세 §6.1 — {@code GET /schedules/{scheduleId}/route} 통합 테스트.
 *
 * <p>{@link RouteService}는 {@code @MockitoBean}으로 대체 — ODsay 실 호출 없이 controller
 * 계약 분기만 검증 (cache hit/miss 동작 흐름 자체는 {@code OdsayRouteServiceTest} 단위 책임).
 * 회귀 가드 범위: 인증/인가/sch_ prefix strip / forceRefresh 전파 / 에러 매핑(404/403/401/502/503/504) /
 * 응답 직렬화(scheduleId·route 6필드·calculatedAt).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RouteControllerIntegrationTest {

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
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean RouteService routeService;

    @Test
    void 정상_GET_route_200_응답_RouteResponse_반환() throws Exception {
        // refresh는 happy path로 stub
        when(routeService.refreshRouteSync(any(Schedule.class))).thenReturn(false);

        // getRoute 응답 stub — 실 OdsayRouteService.getRoute의 cache miss + 정상 흐름 시뮬레이션:
        // applyToSchedule가 schedule.updateRouteInfo로 routeCalculatedAt 갱신해야 RouteResponse.calculatedAt이 non-null.
        when(routeService.getRoute(any(Schedule.class), eq(false))).thenAnswer(inv -> {
            Schedule s = inv.getArgument(0);
            s.updateRouteInfo(34, s.getArrivalTime().minusMinutes(34),
                    "{\"path\":{},\"lane\":null}", OffsetDateTime.now(KST));
            return RouteResponse.of(s, fakeRoute(34));
        });

        String token = signupAndGetToken("routeget01", "조회테스트");
        String scheduleId = createSchedule(token);

        mockMvc.perform(get("/api/v1/schedules/" + scheduleId + "/route")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                // 명세 §6.1 응답 예시 — RouteResponse 6필드 + calculatedAt 직렬화 회귀 가드
                .andExpect(jsonPath("$.data.scheduleId").value(scheduleId))
                .andExpect(jsonPath("$.data.calculatedAt").exists())
                .andExpect(jsonPath("$.data.route.totalDurationMinutes").value(34))
                .andExpect(jsonPath("$.data.route.totalDistanceMeters").value(8704))
                .andExpect(jsonPath("$.data.route.totalWalkMeters").value(319))
                .andExpect(jsonPath("$.data.route.transferCount").value(1))
                .andExpect(jsonPath("$.data.route.payment").value(1500))
                .andExpect(jsonPath("$.data.route.segments").isArray())
                .andExpect(jsonPath("$.data.route.segments[0].mode").value("WALK"))
                .andExpect(jsonPath("$.data.route.segments[0].path").isArray());

        verify(routeService, times(1)).getRoute(any(Schedule.class), eq(false));
    }

    @Test
    void forceRefresh_true_쿼리파라미터_RouteService에_전달() throws Exception {
        when(routeService.refreshRouteSync(any(Schedule.class))).thenReturn(false);
        when(routeService.getRoute(any(Schedule.class), eq(true))).thenAnswer(inv -> {
            Schedule s = inv.getArgument(0);
            return RouteResponse.of(s, fakeRoute(34));
        });

        String token = signupAndGetToken("routeforce01", "강제새로고침");
        String scheduleId = createSchedule(token);

        mockMvc.perform(get("/api/v1/schedules/" + scheduleId + "/route?forceRefresh=true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        verify(routeService, times(1)).getRoute(any(Schedule.class), eq(true));
    }

    @Test
    void 존재하지_않는_scheduleId_404_SCHEDULE_NOT_FOUND() throws Exception {
        String token = signupAndGetToken("routenotfound01", "없는일정");

        mockMvc.perform(get("/api/v1/schedules/sch_01HXXNOEXIST123456789ABCDE/route")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SCHEDULE_NOT_FOUND"));
    }

    @Test
    void 다른_사용자의_일정이면_403_FORBIDDEN_RESOURCE() throws Exception {
        when(routeService.refreshRouteSync(any(Schedule.class))).thenReturn(false);
        String ownerToken = signupAndGetToken("routeowner01", "소유자");
        String scheduleId = createSchedule(ownerToken);

        // 다른 사용자가 같은 schedule에 접근
        String otherToken = signupAndGetToken("routeother01", "다른사용자");

        mockMvc.perform(get("/api/v1/schedules/" + scheduleId + "/route")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN_RESOURCE"));
    }

    @Test
    void 인증_없으면_401() throws Exception {
        mockMvc.perform(get("/api/v1/schedules/sch_01HXX0000000000000000ABCDE/route"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void RouteService가_502_EXTERNAL_ROUTE_API_FAILED_throw하면_502_응답() throws Exception {
        when(routeService.refreshRouteSync(any(Schedule.class))).thenReturn(false);
        when(routeService.getRoute(any(Schedule.class), any(Boolean.class)))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_ROUTE_API_FAILED));

        String token = signupAndGetToken("route502err01", "502테스트");
        String scheduleId = createSchedule(token);

        mockMvc.perform(get("/api/v1/schedules/" + scheduleId + "/route")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("EXTERNAL_ROUTE_API_FAILED"));
    }

    @Test
    void RouteService가_504_EXTERNAL_TIMEOUT_throw하면_504_응답() throws Exception {
        when(routeService.refreshRouteSync(any(Schedule.class))).thenReturn(false);
        when(routeService.getRoute(any(Schedule.class), any(Boolean.class)))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_TIMEOUT));

        String token = signupAndGetToken("route504err01", "504테스트");
        String scheduleId = createSchedule(token);

        mockMvc.perform(get("/api/v1/schedules/" + scheduleId + "/route")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error.code").value("EXTERNAL_TIMEOUT"));
    }

    @Test
    void forceRefresh_파라미터_누락시_디폴트_false_RouteService에_전달() throws Exception {
        // 명세 §6.1 — forceRefresh 미지정 시 디폴트 false. URL에 ?forceRefresh 자체 omit.
        when(routeService.refreshRouteSync(any(Schedule.class))).thenReturn(false);
        when(routeService.getRoute(any(Schedule.class), eq(false))).thenAnswer(inv -> {
            Schedule s = inv.getArgument(0);
            return RouteResponse.of(s, fakeRoute(34));
        });

        String token = signupAndGetToken("routedefault01", "디폴트false");
        String scheduleId = createSchedule(token);

        mockMvc.perform(get("/api/v1/schedules/" + scheduleId + "/route")  // 쿼리 파라미터 omit
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        verify(routeService, times(1)).getRoute(any(Schedule.class), eq(false));
    }

    @Test
    void RouteService가_503_EXTERNAL_AUTH_MISCONFIGURED_throw하면_503_응답() throws Exception {
        when(routeService.refreshRouteSync(any(Schedule.class))).thenReturn(false);
        when(routeService.getRoute(any(Schedule.class), any(Boolean.class)))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_AUTH_MISCONFIGURED));

        String token = signupAndGetToken("route503err01", "503테스트");
        String scheduleId = createSchedule(token);

        mockMvc.perform(get("/api/v1/schedules/" + scheduleId + "/route")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("EXTERNAL_AUTH_MISCONFIGURED"));
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

    private String createSchedule(String accessToken) throws Exception {
        OffsetDateTime arrival = OffsetDateTime.now(KST).plusMinutes(60);
        OffsetDateTime depart = arrival.minusMinutes(30);
        String body = """
                {
                  "title": "경로조회테스트",
                  "origin": {"name":"우이동","lat":37.6612,"lng":127.0124},
                  "destination": {"name":"국민대","lat":37.6103,"lng":126.9969},
                  "userDepartureTime": "%s",
                  "arrivalTime": "%s",
                  "reminderOffsetMinutes": 5
                }
                """.formatted(depart, arrival);

        String resp = mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("scheduleId").asText();
    }

    private static Route fakeRoute(int totalDurationMinutes) {
        // RouteSegment record invariant 통과 — WALK 2점 path
        RouteSegment walk = new RouteSegment(
                SegmentMode.WALK, totalDurationMinutes, 1000,
                null, null, null, null, null, null, null,
                List.of(new double[]{127.0, 37.6}, new double[]{127.1, 37.5})
        );
        return new Route(totalDurationMinutes, 8704, 319, 1, 1500, List.of(walk));
    }
}
