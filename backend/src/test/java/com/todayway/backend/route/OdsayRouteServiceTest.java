package com.todayway.backend.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import com.todayway.backend.external.ExternalApiException;
import com.todayway.backend.external.odsay.OdsayClient;
import com.todayway.backend.external.odsay.OdsayProperties;
import com.todayway.backend.schedule.domain.Schedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OdsayRouteService} 단위 테스트 — 명세 §5.1/§6.1 분기 전체 회귀 가드.
 *
 * <p>OdsayClient/OdsayResponseMapper는 mock, Schedule은 real (도메인 메서드 검증 필요).
 * Clock은 fixed clock으로 시간 분기 deterministic.
 */
@ExtendWith(MockitoExtension.class)
class OdsayRouteServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final OffsetDateTime FIXED_NOW = OffsetDateTime.parse("2026-04-30T10:00:00+09:00");

    @Mock OdsayClient odsayClient;
    @Mock OdsayResponseMapper mapper;

    OdsayProperties properties;
    ObjectMapper objectMapper;
    Clock fixedClock;
    OdsayRouteService service;

    @BeforeEach
    void setUp() {
        properties = new OdsayProperties();
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://api.odsay.com/v1/api");
        properties.setTimeoutSeconds(5);
        properties.setCacheTtlMinutes(10);
        objectMapper = new ObjectMapper();
        fixedClock = Clock.fixed(FIXED_NOW.toInstant(), KST);
        service = new OdsayRouteService(odsayClient, mapper, properties, objectMapper, fixedClock);
    }

    // ─── refreshRouteSync ─────────────────────────────────────────

    @Test
    void refreshRouteSync_성공시_schedule_갱신_true반환() {
        Schedule s = newSchedule();
        when(odsayClient.searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn("{\"raw\":\"json\"}");
        when(mapper.toRoute(anyString(), nullable(String.class), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(fakeRoute(34));

        boolean result = service.refreshRouteSync(s);

        assertThat(result).isTrue();
        assertThat(s.getEstimatedDurationMinutes()).isEqualTo(34);
        // wrapped 형식 — {"path": <pathRaw>, "lane": null} (mapObj 없는 stub이라 lane null fallback)
        assertThat(s.getRouteSummaryJson()).isEqualTo("{\"path\":{\"raw\":\"json\"},\"lane\":null}");
        assertThat(s.getRouteCalculatedAt()).isEqualTo(FIXED_NOW);
        // 명세 §5.1 — recommended_departure_time = arrival_time - estimated_duration_minutes
        assertThat(s.getRecommendedDepartureTime())
                .isEqualTo(s.getArrivalTime().minusMinutes(34));
    }

    @Test
    void refreshRouteSync_401이면_BusinessException_503_AUTH_MISCONFIGURED_propagate() {
        // 401/403은 운영자 조치 필요 — silent false 반환 X. 일정 등록 시점에도 동일 정책.
        Schedule s = newSchedule();
        when(odsayClient.searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new ExternalApiException(
                        ExternalApiException.Source.ODSAY,
                        ExternalApiException.Type.CLIENT_ERROR, 401, "auth", null));

        BusinessException ex = catchThrowableOfType(
                BusinessException.class, () -> service.refreshRouteSync(s));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_AUTH_MISCONFIGURED);
        assertThat(s.getRouteSummaryJson()).isNull();
    }

    @Test
    void refreshRouteSync_403이면_BusinessException_503_AUTH_MISCONFIGURED_propagate() {
        Schedule s = newSchedule();
        when(odsayClient.searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new ExternalApiException(
                        ExternalApiException.Source.ODSAY,
                        ExternalApiException.Type.CLIENT_ERROR, 403, "forbidden", null));

        BusinessException ex = catchThrowableOfType(
                BusinessException.class, () -> service.refreshRouteSync(s));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_AUTH_MISCONFIGURED);
    }

    @Test
    void refreshRouteSync_ExternalApiException은_graceful_false반환_schedule변경없음() {
        Schedule s = newSchedule();
        when(odsayClient.searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new ExternalApiException(
                        ExternalApiException.Source.ODSAY,
                        ExternalApiException.Type.TIMEOUT, null, "timeout", null));

        boolean result = service.refreshRouteSync(s);

        assertThat(result).isFalse();
        // graceful — Schedule의 ODsay 필드 모두 null 유지
        assertThat(s.getRouteSummaryJson()).isNull();
        assertThat(s.getEstimatedDurationMinutes()).isNull();
        assertThat(s.getRouteCalculatedAt()).isNull();
    }

    @Test
    void refreshRouteSync_응답매핑실패_IllegalStateException도_graceful() {
        Schedule s = newSchedule();
        when(odsayClient.searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn("{}");
        when(mapper.toRoute(anyString(), nullable(String.class), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new IllegalStateException("path[0] 없음"));

        boolean result = service.refreshRouteSync(s);

        assertThat(result).isFalse();
        assertThat(s.getRouteSummaryJson()).isNull();
    }

    // ─── getRoute ─────────────────────────────────────────────────

    @Test
    void getRoute_cache_hit_TTL_내면_ODsay_호출안함() {
        Schedule s = newScheduleWithCache(FIXED_NOW.minusMinutes(5));   // TTL=10, 5분 전 → hit
        when(mapper.toRoute(anyString(), nullable(String.class), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(fakeRoute(34));

        RouteResponse res = service.getRoute(s, false);

        assertThat(res.scheduleId()).startsWith("sch_");
        assertThat(res.calculatedAt()).isEqualTo(FIXED_NOW.minusMinutes(5));
        verify(odsayClient, never())
                .searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void getRoute_cache_정확히_TTL_경계_10분이면_expired_처리() {
        // isCacheValid가 < 사용 — 정확히 10분 시점은 expired. < ↔ <= 변경 시 회귀 가드.
        Schedule s = newScheduleWithCache(FIXED_NOW.minusMinutes(10));
        when(odsayClient.searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn("{\"fresh\":true}");
        when(mapper.toRoute(anyString(), nullable(String.class), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(fakeRoute(34));

        service.getRoute(s, false);

        verify(odsayClient, times(1))
                .searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void getRoute_cache_expired_TTL_초과시_ODsay_재호출() {
        Schedule s = newScheduleWithCache(FIXED_NOW.minusMinutes(15));  // TTL=10, 15분 전 → expired
        when(odsayClient.searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn("{\"fresh\":true}");
        when(mapper.toRoute(anyString(), nullable(String.class), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(fakeRoute(34));

        service.getRoute(s, false);

        verify(odsayClient, times(1))
                .searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        // cache 갱신 검증
        assertThat(s.getRouteCalculatedAt()).isEqualTo(FIXED_NOW);
        assertThat(s.getRouteSummaryJson()).isEqualTo("{\"path\":{\"fresh\":true},\"lane\":null}");
    }

    @Test
    void getRoute_forceRefresh_true면_TTL내_캐시도_무시하고_ODsay_재호출() {
        Schedule s = newScheduleWithCache(FIXED_NOW.minusMinutes(1));   // 1분 전 — TTL 내
        when(odsayClient.searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn("{\"forced\":true}");
        when(mapper.toRoute(anyString(), nullable(String.class), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(fakeRoute(34));

        service.getRoute(s, true);

        verify(odsayClient, times(1))
                .searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void getRoute_ODsay실패_캐시있으면_stale_응답_BusinessException_안던짐() {
        // 명세 §6.1 비고 — "캐시가 있으면 ODsay 실패 시에도 캐시로 응답 (캐시 stale 허용)"
        Schedule s = newScheduleWithCache(FIXED_NOW.minusMinutes(15));  // expired
        when(odsayClient.searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new ExternalApiException(
                        ExternalApiException.Source.ODSAY,
                        ExternalApiException.Type.SERVER_ERROR, 500, "5xx", null));
        when(mapper.toRoute(anyString(), nullable(String.class), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(fakeRoute(34));

        RouteResponse res = service.getRoute(s, false);

        assertThat(res).isNotNull();
        // stale — calculatedAt은 갱신되지 않음 (15분 전 시각 그대로)
        assertThat(res.calculatedAt()).isEqualTo(FIXED_NOW.minusMinutes(15));
    }

    @Test
    void getRoute_ODsay_TIMEOUT_캐시없으면_BusinessException_504() {
        Schedule s = newSchedule();  // 캐시 없음 (생성 직후)
        when(odsayClient.searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new ExternalApiException(
                        ExternalApiException.Source.ODSAY,
                        ExternalApiException.Type.TIMEOUT, null, "timeout", null));

        BusinessException ex = catchThrowableOfType(
                BusinessException.class, () -> service.getRoute(s, false));

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_TIMEOUT);
    }

    @Test
    void getRoute_ODsay_401_캐시없으면_BusinessException_503_AUTH_MISCONFIGURED() {
        Schedule s = newSchedule();
        when(odsayClient.searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new ExternalApiException(
                        ExternalApiException.Source.ODSAY,
                        ExternalApiException.Type.CLIENT_ERROR, 401, "auth", null));

        BusinessException ex = catchThrowableOfType(
                BusinessException.class, () -> service.getRoute(s, false));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_AUTH_MISCONFIGURED);
    }

    @Test
    void getRoute_loadLane_정상호출_wrapped_저장() {
        // §6.1 v1.1.10 — searchPubTransPathT 응답에 mapObj 있으면 loadLane 호출 + wrapped 저장
        Schedule s = newSchedule();
        String pathRaw = "{\"result\":{\"path\":[{\"info\":{\"mapObj\":\"908:1:1:16\"}}]}}";
        when(odsayClient.searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(pathRaw);
        when(odsayClient.loadLane("908:1:1:16")).thenReturn("{\"lane_data\":true}");
        when(mapper.toRoute(anyString(), nullable(String.class), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(fakeRoute(34));

        service.getRoute(s, false);

        verify(odsayClient, times(1)).loadLane("908:1:1:16");
        // wrapped 형식 — path + lane 둘 다 보존
        assertThat(s.getRouteSummaryJson())
                .contains("\"path\":")
                .contains("\"mapObj\":\"908:1:1:16\"")
                .contains("\"lane\":{\"lane_data\":true}");
    }

    @Test
    void getRoute_loadLane_실패시_graceful_lane_null_저장() {
        // loadLane이 5xx여도 path 매핑은 정상 진행 + lane=null로 저장 (passStopList fallback)
        Schedule s = newSchedule();
        String pathRaw = "{\"result\":{\"path\":[{\"info\":{\"mapObj\":\"x:y\"}}]}}";
        when(odsayClient.searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(pathRaw);
        when(odsayClient.loadLane(anyString()))
                .thenThrow(new ExternalApiException(
                        ExternalApiException.Source.ODSAY,
                        ExternalApiException.Type.SERVER_ERROR, 500, "5xx", null));
        when(mapper.toRoute(anyString(), nullable(String.class), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(fakeRoute(34));

        service.getRoute(s, false);

        assertThat(s.getRouteSummaryJson()).contains("\"lane\":null");
    }

    @Test
    void getRoute_mapObj_없으면_loadLane_호출_안됨() {
        // ODsay 응답에 info.mapObj 없으면 loadLane 호출 자체 skip — 불필요 호출 방지
        Schedule s = newSchedule();
        when(odsayClient.searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn("{\"result\":{\"path\":[{\"info\":{}}]}}");
        when(mapper.toRoute(anyString(), nullable(String.class), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(fakeRoute(34));

        service.getRoute(s, false);

        verify(odsayClient, never()).loadLane(anyString());
        assertThat(s.getRouteSummaryJson()).contains("\"lane\":null");
    }

    @Test
    void getRoute_ODsay_5xx_캐시없으면_BusinessException_502() {
        Schedule s = newSchedule();
        when(odsayClient.searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new ExternalApiException(
                        ExternalApiException.Source.ODSAY,
                        ExternalApiException.Type.SERVER_ERROR, 500, "5xx", null));

        BusinessException ex = catchThrowableOfType(
                BusinessException.class, () -> service.getRoute(s, false));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_ROUTE_API_FAILED);
    }

    @Test
    void getRoute_cache_hit_이지만_캐시raw_매핑실패시_fresh_호출로_복구() {
        // 캐시 TTL 내(5분 전)이지만 저장된 raw가 손상됐다고 가정 → mapper 첫 호출은 실패.
        // fresh ODsay 호출 + 두 번째 매핑은 성공해야 200 응답.
        Schedule s = newScheduleWithCache(FIXED_NOW.minusMinutes(5));
        when(mapper.toRoute(anyString(), nullable(String.class), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new IllegalStateException("path[0] 없음"))   // (1) tryMapCache 실패
                .thenReturn(fakeRoute(34));                             // (2) fresh 매핑 성공
        when(odsayClient.searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn("{\"recovered\":true}");

        RouteResponse res = service.getRoute(s, false);

        assertThat(res).isNotNull();
        // ODsay 호출됐고 cache 갱신됨 (calculatedAt = FIXED_NOW)
        verify(odsayClient, times(1))
                .searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        assertThat(s.getRouteCalculatedAt()).isEqualTo(FIXED_NOW);
        assertThat(s.getRouteSummaryJson()).isEqualTo("{\"path\":{\"recovered\":true},\"lane\":null}");
    }

    @Test
    void getRoute_fresh_응답_매핑실패_캐시있으면_stale_fallback() {
        // §6.1 비고 — fresh 응답이 깨졌어도 캐시가 살아있으면 stale로 응답.
        // 첫 매핑(fresh)은 실패, 두 번째(tryMapCache stale)는 성공.
        Schedule s = newScheduleWithCache(FIXED_NOW.minusMinutes(15));   // expired
        when(odsayClient.searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn("{\"corrupt\":true}");
        when(mapper.toRoute(anyString(), nullable(String.class), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new IllegalStateException("path[0] 없음"))   // (1) fresh 매핑 실패
                .thenReturn(fakeRoute(34));                             // (2) stale 매핑 성공

        RouteResponse res = service.getRoute(s, false);

        assertThat(res).isNotNull();
        // stale — calculatedAt은 갱신되지 않음 (15분 전 그대로), routeSummaryJson도 보존
        assertThat(res.calculatedAt()).isEqualTo(FIXED_NOW.minusMinutes(15));
        assertThat(s.getRouteSummaryJson()).isEqualTo("{\"path\":{\"cached\":true},\"lane\":null}");
    }

    // ─── unwrapRaw / wrapRaw 분기 (§6.1 v1.1.10) ────────────────

    @Test
    void getRoute_캐시_wrapped_path_없으면_unwrap_실패_fresh_복구() {
        // 잘못 저장된 캐시 (path 키 없음) → unwrapRaw IllegalStateException → tryMapCache empty → fresh.
        // v1.1.10 이전 raw가 그대로 남아있는 경우 자동 마이그레이션 (운영 spike 가능성 P1 T2).
        Schedule s = newSchedule();
        s.updateRouteInfo(34, s.getArrivalTime().minusMinutes(34),
                "{\"result\":{\"path\":[]}}",  // v1.1.10 이전 형식: path 키 없이 ODsay raw 그대로
                FIXED_NOW.minusMinutes(5));   // TTL 내
        when(odsayClient.searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn("{\"result\":{\"path\":[{\"info\":{}}]}}");
        when(mapper.toRoute(anyString(), nullable(String.class), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(fakeRoute(34));

        service.getRoute(s, false);

        verify(odsayClient, times(1))
                .searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void getRoute_캐시_wrapped_lane이_텍스트_null이면_unwrap_거부_fresh_복구() {
        // lane 노드가 text "null" 또는 비-object 형태면 명시 거부 (M1 안전망)
        Schedule s = newSchedule();
        s.updateRouteInfo(34, s.getArrivalTime().minusMinutes(34),
                "{\"path\":{\"a\":1},\"lane\":\"null\"}",  // lane이 string "null"
                FIXED_NOW.minusMinutes(5));
        when(odsayClient.searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn("{\"result\":{\"path\":[{\"info\":{}}]}}");
        when(mapper.toRoute(anyString(), nullable(String.class), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(fakeRoute(34));

        service.getRoute(s, false);

        verify(odsayClient, times(1))
                .searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void getRoute_loadLane_401이면_503_AUTH_MISCONFIGURED_propagate() {
        // M3: loadLane이 401/403이면 운영자 alert 필요 → graceful 흡수 X, BusinessException으로 격상
        Schedule s = newSchedule();
        when(odsayClient.searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn("{\"result\":{\"path\":[{\"info\":{\"mapObj\":\"x:y\"}}]}}");
        when(odsayClient.loadLane(anyString()))
                .thenThrow(new ExternalApiException(
                        ExternalApiException.Source.ODSAY,
                        ExternalApiException.Type.CLIENT_ERROR, 401, "auth", null));

        BusinessException ex = catchThrowableOfType(
                BusinessException.class, () -> service.getRoute(s, false));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_AUTH_MISCONFIGURED);
    }

    @Test
    void getRoute_loadLane_403이면_503_AUTH_MISCONFIGURED_propagate() {
        Schedule s = newSchedule();
        when(odsayClient.searchPubTransPathT(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn("{\"result\":{\"path\":[{\"info\":{\"mapObj\":\"x:y\"}}]}}");
        when(odsayClient.loadLane(anyString()))
                .thenThrow(new ExternalApiException(
                        ExternalApiException.Source.ODSAY,
                        ExternalApiException.Type.CLIENT_ERROR, 403, "forbidden", null));

        BusinessException ex = catchThrowableOfType(
                BusinessException.class, () -> service.getRoute(s, false));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_AUTH_MISCONFIGURED);
    }

    // ─── helpers ──────────────────────────────────────────────────

    /**
     * Schedule.create() 직접 호출. arrival = FIXED_NOW + 30분, depart = FIXED_NOW - 1시간.
     * routeSummaryJson/routeCalculatedAt 모두 null (cache 없는 신규 일정).
     */
    private static Schedule newSchedule() {
        return Schedule.create(
                1L, "국민대 등교",
                "국민대", new BigDecimal("37.611"), new BigDecimal("126.997"),
                null, null, null,
                "서울시청", new BigDecimal("37.5665"), new BigDecimal("126.978"),
                null, null, null,
                FIXED_NOW.minusHours(1), FIXED_NOW.plusMinutes(30),
                5, null, null, null
        );
    }

    /**
     * 캐시 보유 Schedule. routeSummaryJson은 wrapped 형식 (§6.1 v1.1.10) —
     * {@code {"path":..., "lane":null}}. lane=null로 두면 mapper가 passStopList fallback.
     */
    private static Schedule newScheduleWithCache(OffsetDateTime calculatedAt) {
        Schedule s = newSchedule();
        s.updateRouteInfo(
                34,
                s.getArrivalTime().minusMinutes(34),
                "{\"path\":{\"cached\":true},\"lane\":null}",
                calculatedAt
        );
        return s;
    }

    private static Route fakeRoute(int totalDurationMinutes) {
        return new Route(totalDurationMinutes, 8704, 319, 1, 1500, List.of());
    }
}
