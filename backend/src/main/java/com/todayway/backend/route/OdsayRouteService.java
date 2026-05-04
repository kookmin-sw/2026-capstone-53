package com.todayway.backend.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import com.todayway.backend.external.ExternalApiException;
import com.todayway.backend.external.odsay.OdsayClient;
import com.todayway.backend.external.odsay.OdsayProperties;
import com.todayway.backend.schedule.domain.Schedule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * {@link RouteService} 구현 — ODsay {@code searchPubTransPathT} 호출 + 응답 매핑 + Schedule 갱신
 * + TTL 캐시 fallback. 명세 §5.1 / §6.1 정합.
 *
 * <h3>{@code refreshRouteSync} (Schedule 등록/수정 흐름)</h3>
 * <p>실패 시 graceful degradation — {@code ScheduleService.create()}는 ODsay 결과 없이 등록 성공.
 * 클라이언트는 {@code routeStatus = PENDING_RETRY}를 받음
 * ({@code Schedule.hasCalculatedRoute()}가 false라).
 *
 * <h3>{@code getRoute} (조회 흐름)</h3>
 * <p>4단계 fallback:
 * <ol>
 *   <li>cache hit (TTL 내) → DB raw 그대로 매핑 응답</li>
 *   <li>cache miss / forceRefresh → ODsay 호출 + DB 저장 + 응답</li>
 *   <li>ODsay 실패 + 기존 raw 있음 → stale 응답 (명세 §6.1 비고 — "캐시 stale 허용")</li>
 *   <li>ODsay 실패 + raw 없음 → {@link BusinessException} throw (502/503/504, §1.6)</li>
 * </ol>
 */
@Service
@Slf4j
public class OdsayRouteService implements RouteService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final OdsayClient odsayClient;
    private final OdsayResponseMapper mapper;
    private final OdsayProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public OdsayRouteService(OdsayClient odsayClient,
                             OdsayResponseMapper mapper,
                             OdsayProperties properties,
                             ObjectMapper objectMapper) {
        this(odsayClient, mapper, properties, objectMapper, Clock.system(KST));
    }

    /** 테스트용 — fixed Clock 주입으로 시간 검증 deterministic하게. */
    OdsayRouteService(OdsayClient odsayClient,
                      OdsayResponseMapper mapper,
                      OdsayProperties properties,
                      ObjectMapper objectMapper,
                      Clock clock) {
        this.odsayClient = odsayClient;
        this.mapper = mapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** {@code searchPubTransPathT} raw + {@code loadLane} raw 묶음. lane은 graceful 정책으로 nullable. */
    private record OdsayRaw(String pathRaw, String laneRaw) {}

    @Override
    public boolean refreshRouteSync(Schedule schedule) {
        try {
            OdsayRaw raw = callOdsay(schedule);
            Route route = mapToRoute(schedule, raw);
            applyToSchedule(schedule, route, raw);
            return true;
        } catch (ExternalApiException e) {
            // graceful degradation — ScheduleService.create()/update()는 ODsay 결과 없이 진행.
            log.warn("ODsay refresh 실패 (graceful) scheduleUid={} type={} httpStatus={}",
                    schedule.getScheduleUid(), e.getType(), e.getHttpStatus(), e);
            return false;
        } catch (IllegalStateException | IllegalArgumentException e) {
            // 응답 매핑 실패 (path[0] 없음, unknown trafficType, 좌표 파싱 실패) — 동일 graceful 처리.
            log.warn("ODsay 응답 매핑 실패 (graceful) scheduleUid={}",
                    schedule.getScheduleUid(), e);
            return false;
        }
    }

    @Override
    public RouteResponse getRoute(Schedule schedule, boolean forceRefresh) {
        // (1) cache hit — TTL 내 + 매핑 성공 시 ODsay 호출 없이 응답
        if (!forceRefresh && isCacheValid(schedule)) {
            Optional<Route> cached = tryMapCache(schedule);
            if (cached.isPresent()) {
                return buildResponse(schedule, cached.get());
            }
            // 캐시 raw가 깨진 경우 — 아래 fresh 호출로 자연스럽게 복구 시도.
        }

        // (2) cache miss / expired / forceRefresh / 캐시 손상 — ODsay 호출
        try {
            OdsayRaw raw = callOdsay(schedule);
            Route route = mapToRoute(schedule, raw);
            applyToSchedule(schedule, route, raw);
            return buildResponse(schedule, route);
        } catch (ExternalApiException e) {
            // (3) ODsay 실패 + 매핑 가능한 캐시 있음 → stale 응답 (§6.1 비고)
            Optional<Route> stale = tryMapCache(schedule);
            if (stale.isPresent()) {
                log.warn("ODsay 실패 — stale 응답 scheduleUid={} type={} httpStatus={}",
                        schedule.getScheduleUid(), e.getType(), e.getHttpStatus(), e);
                return buildResponse(schedule, stale.get());
            }
            // (4) ODsay 실패 + 캐시 없음/손상 → §1.6 매핑된 BusinessException throw
            throw mapToBusinessException(e);
        } catch (IllegalStateException | IllegalArgumentException e) {
            // fresh 응답 매핑 실패 — 캐시가 살아있으면 stale로 fallback (§6.1 비고)
            Optional<Route> stale = tryMapCache(schedule);
            if (stale.isPresent()) {
                log.warn("ODsay fresh 응답 매핑 실패 — stale 응답 scheduleUid={}",
                        schedule.getScheduleUid(), e);
                return buildResponse(schedule, stale.get());
            }
            log.warn("ODsay 응답 매핑 실패 scheduleUid={}", schedule.getScheduleUid(), e);
            throw new BusinessException(ErrorCode.EXTERNAL_ROUTE_API_FAILED);
        }
    }

    // ── helpers ──

    /**
     * ODsay 2회 호출 — {@code searchPubTransPathT} 후 응답의 {@code info.mapObj}로 {@code loadLane}.
     * loadLane은 graceful — 실패 시 {@code laneRaw=null}로 묶어 반환 (mapper가 passStopList 직선 fallback).
     * <p>{@code searchPubTransPathT} 자체 실패는 throw — 호출자가 graceful catch.
     */
    private OdsayRaw callOdsay(Schedule schedule) {
        String pathRaw = odsayClient.searchPubTransPathT(
                bd(schedule.getOriginLng()),
                bd(schedule.getOriginLat()),
                bd(schedule.getDestinationLng()),
                bd(schedule.getDestinationLat())
        );
        String laneRaw = tryLoadLane(pathRaw);
        return new OdsayRaw(pathRaw, laneRaw);
    }

    /** loadLane graceful 호출 — mapObj 추출/호출 실패 시 null. mapper가 passStopList fallback. */
    private String tryLoadLane(String pathRaw) {
        String mapObj;
        try {
            JsonNode root = objectMapper.readTree(pathRaw);
            mapObj = root.path("result").path("path").path(0).path("info").path("mapObj").asText(null);
        } catch (Exception e) {
            log.warn("ODsay loadLane mapObj 추출 실패 — passStopList 직선 fallback", e);
            return null;
        }
        if (mapObj == null || mapObj.isBlank()) {
            log.warn("ODsay 응답에 info.mapObj 없음 — passStopList 직선 fallback");
            return null;
        }
        try {
            return odsayClient.loadLane(mapObj);
        } catch (ExternalApiException e) {
            log.warn("ODsay loadLane 호출 실패 (graceful) type={} httpStatus={}",
                    e.getType(), e.getHttpStatus(), e);
            return null;
        }
    }

    private Route mapToRoute(Schedule schedule, OdsayRaw raw) {
        return mapper.toRoute(raw.pathRaw(), raw.laneRaw(),
                bd(schedule.getOriginLng()), bd(schedule.getOriginLat()),
                bd(schedule.getDestinationLng()), bd(schedule.getDestinationLat()));
    }

    /**
     * 저장된 wrapped raw({@code {"path":..., "lane":...}})를 unwrap → mapper 매핑.
     * raw 없음/형식 위반/매핑 실패 시 {@link Optional#empty()} — caller가 다음 fallback으로 진행.
     * <p>cache hit 경로(§6.1)와 ODsay 실패 stale fallback 경로 양쪽에서 동일하게 사용.
     */
    private Optional<Route> tryMapCache(Schedule schedule) {
        String wrapped = schedule.getRouteSummaryJson();
        if (wrapped == null) {
            return Optional.empty();
        }
        try {
            OdsayRaw raw = unwrapRaw(wrapped);
            return Optional.of(mapToRoute(schedule, raw));
        } catch (IllegalStateException | IllegalArgumentException e) {
            log.warn("캐시된 ODsay raw 매핑 실패 scheduleUid={}",
                    schedule.getScheduleUid(), e);
            return Optional.empty();
        }
    }

    /**
     * ODsay 응답 + Route 매핑 결과를 Schedule 엔티티에 반영.
     * <p>{@code recommended_departure_time = arrival_time - estimated_duration_minutes}
     * (명세 §5.1 DB 매핑). Schedule.updateRouteInfo가 자동으로 departureAdvice/reminderAt 재계산.
     * <p>{@code routeSummaryJson}은 wrapped 형식 — {@code {"path":..., "lane":...}} (§6.1 v1.1.10).
     */
    private void applyToSchedule(Schedule schedule, Route route, OdsayRaw raw) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime recommendedDeparture = schedule.getArrivalTime()
                .minusMinutes(route.totalDurationMinutes());
        schedule.updateRouteInfo(
                route.totalDurationMinutes(),
                recommendedDeparture,
                wrapRaw(raw),
                now
        );
    }

    /** {@code {"path": <pathRaw>, "lane": <laneRaw|null>}} — lane은 graceful로 null 가능. */
    private String wrapRaw(OdsayRaw raw) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.set("path", objectMapper.readTree(raw.pathRaw()));
            node.set("lane", raw.laneRaw() == null
                    ? objectMapper.nullNode()
                    : objectMapper.readTree(raw.laneRaw()));
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            // pathRaw는 ODsay에서 받은 valid JSON이라 정상적으로는 발생 X. 안전망.
            throw new IllegalStateException("ODsay raw wrapping 실패", e);
        }
    }

    /** wrapped JSON에서 path/lane raw 분리. 형식 위반은 IllegalStateException으로 throw. */
    private OdsayRaw unwrapRaw(String wrapped) {
        try {
            JsonNode root = objectMapper.readTree(wrapped);
            JsonNode pathNode = root.path("path");
            JsonNode laneNode = root.path("lane");
            if (pathNode.isMissingNode() || pathNode.isNull()) {
                throw new IllegalStateException("캐시된 routeSummaryJson에 path 없음");
            }
            String pathRaw = objectMapper.writeValueAsString(pathNode);
            String laneRaw = laneNode.isMissingNode() || laneNode.isNull()
                    ? null
                    : objectMapper.writeValueAsString(laneNode);
            return new OdsayRaw(pathRaw, laneRaw);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("캐시된 routeSummaryJson 파싱 실패", e);
        }
    }

    private boolean isCacheValid(Schedule schedule) {
        if (schedule.getRouteSummaryJson() == null || schedule.getRouteCalculatedAt() == null) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        long ageMinutes = Duration.between(schedule.getRouteCalculatedAt(), now).toMinutes();
        return ageMinutes < properties.getCacheTtlMinutes();
    }

    private RouteResponse buildResponse(Schedule schedule, Route route) {
        // sch_ prefix 처리는 RouteResponse.of() 정적 팩토리로 위임 (ScheduleResponse 패턴 일관)
        return RouteResponse.of(schedule, route);
    }

    /** {@link BigDecimal} → {@code double}. ODsay 좌표는 소수점 7자리 이내라 정밀도 손실 없음. */
    private static double bd(BigDecimal value) {
        return value.doubleValue();
    }

    /**
     * {@link ExternalApiException} → {@link BusinessException} + {@link ErrorCode} 매핑 (명세 §1.6).
     *
     * <ul>
     *   <li>{@code TIMEOUT} → 504 {@code EXTERNAL_TIMEOUT}</li>
     *   <li>{@code CLIENT_ERROR} + httpStatus {@code 401/403} → 503 {@code EXTERNAL_AUTH_MISCONFIGURED}
     *       (운영자 조치 필요 — 일반 외부 일시 장애와 구분)</li>
     *   <li>{@code CLIENT_ERROR} (그 외) / {@code SERVER_ERROR} / {@code NETWORK} →
     *       502 {@code EXTERNAL_ROUTE_API_FAILED}</li>
     * </ul>
     */
    private static BusinessException mapToBusinessException(ExternalApiException e) {
        return switch (e.getType()) {
            case TIMEOUT -> new BusinessException(ErrorCode.EXTERNAL_TIMEOUT);
            case CLIENT_ERROR -> {
                Integer status = e.getHttpStatus();
                boolean authIssue = status != null && (status == 401 || status == 403);
                yield new BusinessException(
                        authIssue ? ErrorCode.EXTERNAL_AUTH_MISCONFIGURED
                                  : ErrorCode.EXTERNAL_ROUTE_API_FAILED);
            }
            case SERVER_ERROR, NETWORK ->
                    new BusinessException(ErrorCode.EXTERNAL_ROUTE_API_FAILED);
        };
    }
}
