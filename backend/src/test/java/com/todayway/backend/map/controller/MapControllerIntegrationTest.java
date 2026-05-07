package com.todayway.backend.map.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todayway.backend.auth.dto.SignupRequest;
import com.todayway.backend.route.RouteService;
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

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 명세 §4.1 (GET /main) + §4.2 (GET /map/config) 통합 테스트.
 *
 * <p>{@link RouteService} 는 {@code @MockitoBean} — schedule 등록 흐름의 ODsay 호출 격리.
 * push.scheduler.enabled=false 로 통합 테스트 중 자동 트리거 차단.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MapControllerIntegrationTest {

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
        registry.add("push.scheduler.enabled", () -> "false");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean RouteService routeService;

    // ─────────── §4.2 GET /map/config ───────────

    @Test
    void mapConfig_정상_200_명세_4_2_정적_응답() throws Exception {
        mockMvc.perform(get("/api/v1/map/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("NAVER"))
                .andExpect(jsonPath("$.data.defaultZoom").value(15))
                .andExpect(jsonPath("$.data.defaultCenter.lat").value(37.5665))
                .andExpect(jsonPath("$.data.defaultCenter.lng").value(126.9780))
                .andExpect(jsonPath("$.data.tileStyle").value("basic"));
    }

    @Test
    void mapConfig_인증_없이도_200_permitAll() throws Exception {
        // /map/config 는 SecurityConfig.permitAll 등록 — Authorization 헤더 없음에도 200.
        mockMvc.perform(get("/api/v1/map/config"))
                .andExpect(status().isOk());
    }

    // ─────────── §4.1 GET /main ───────────

    @Test
    void main_게스트_토큰없이_200_nearestSchedule_null_mapCenter_default() throws Exception {
        mockMvc.perform(get("/api/v1/main"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nearestSchedule").value(nullValue()))
                .andExpect(jsonPath("$.data.mapCenter.lat").value(37.5665))
                .andExpect(jsonPath("$.data.mapCenter.lng").value(126.9780));
    }

    @Test
    void main_게스트_query_lat_lng_제공시_mapCenter_query() throws Exception {
        mockMvc.perform(get("/api/v1/main?lat=37.66&lng=127.01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nearestSchedule").value(nullValue()))
                .andExpect(jsonPath("$.data.mapCenter.lat").value(37.66))
                .andExpect(jsonPath("$.data.mapCenter.lng").value(127.01));
    }

    @Test
    void main_인증_일정없음_nearestSchedule_null_mapCenter_default() throws Exception {
        String token = signupAndGetToken("mainnoschd01", "일정없음");

        mockMvc.perform(get("/api/v1/main").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nearestSchedule").value(nullValue()))
                .andExpect(jsonPath("$.data.mapCenter.lat").value(37.5665));
    }

    @Test
    void main_인증_일정있음_nearestSchedule_채워지고_mapCenter_origin() throws Exception {
        // refresh 실패 stub (graceful) — schedule 등록은 성공, hasCalculatedRoute=false
        when(routeService.refreshRouteSync(any(Schedule.class))).thenReturn(false);

        String token = signupAndGetToken("mainwithschd01", "일정있음");
        String externalScheduleId = createSchedule(token);

        mockMvc.perform(get("/api/v1/main").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nearestSchedule.scheduleId").value(externalScheduleId))
                .andExpect(jsonPath("$.data.nearestSchedule.title").value("메인테스트"))
                .andExpect(jsonPath("$.data.nearestSchedule.origin.name").value("우이동"))
                .andExpect(jsonPath("$.data.nearestSchedule.origin.lat").value(37.6612))
                .andExpect(jsonPath("$.data.nearestSchedule.origin.lng").value(127.0124))
                .andExpect(jsonPath("$.data.nearestSchedule.destination.name").value("국민대"))
                .andExpect(jsonPath("$.data.nearestSchedule.hasCalculatedRoute").value(false))
                // mapCenter 우선순위 ② nearestSchedule.origin (query lat/lng 없음)
                .andExpect(jsonPath("$.data.mapCenter.lat").value(37.6612))
                .andExpect(jsonPath("$.data.mapCenter.lng").value(127.0124));
    }

    @Test
    void main_인증_일정있고_query_제공시_mapCenter_query_우선() throws Exception {
        when(routeService.refreshRouteSync(any(Schedule.class))).thenReturn(false);
        String token = signupAndGetToken("mainquery01", "쿼리우선");
        createSchedule(token);

        mockMvc.perform(get("/api/v1/main?lat=37.50&lng=127.30")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                // ① query 가 ② origin 보다 우선
                .andExpect(jsonPath("$.data.mapCenter.lat").value(37.50))
                .andExpect(jsonPath("$.data.mapCenter.lng").value(127.30));
    }

    @Test
    void main_인증_일정있고_route_계산완료시_hasCalculatedRoute_true() throws Exception {
        // refreshRouteSync 가 schedule.updateRouteInfo 를 호출해 routeSummaryJson 채움 → hasCalculatedRoute=true
        when(routeService.refreshRouteSync(any(Schedule.class))).thenAnswer(inv -> {
            Schedule s = inv.getArgument(0);
            s.updateRouteInfo(34, s.getArrivalTime().minusMinutes(34),
                    "{\"path\":{},\"lane\":null}", OffsetDateTime.now(KST));
            return true;
        });
        String token = signupAndGetToken("mainroute01", "경로있음");
        createSchedule(token);

        mockMvc.perform(get("/api/v1/main").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nearestSchedule.hasCalculatedRoute").value(true))
                .andExpect(jsonPath("$.data.nearestSchedule.recommendedDepartureTime").exists());
    }

    // ─────────── helpers ───────────

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

    private String createSchedule(String token) throws Exception {
        OffsetDateTime arrival = OffsetDateTime.now(KST).plusMinutes(60);
        OffsetDateTime depart = arrival.minusMinutes(30);
        String body = """
                {
                  "title": "메인테스트",
                  "origin": {"name":"우이동","lat":37.6612,"lng":127.0124},
                  "destination": {"name":"국민대","lat":37.6103,"lng":126.9969},
                  "userDepartureTime": "%s",
                  "arrivalTime": "%s",
                  "reminderOffsetMinutes": 5
                }
                """.formatted(depart, arrival);
        String resp = mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("scheduleId").asText();
    }
}
