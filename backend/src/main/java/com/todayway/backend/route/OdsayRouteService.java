package com.todayway.backend.route;

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
    private final Clock clock;

    @Autowired
    public OdsayRouteService(OdsayClient odsayClient,
                             OdsayResponseMapper mapper,
                             OdsayProperties properties) {
        this(odsayClient, mapper, properties, Clock.system(KST));
    }

    /** 테스트용 — fixed Clock 주입으로 시간 검증 deterministic하게. */
    OdsayRouteService(OdsayClient odsayClient,
                      OdsayResponseMapper mapper,
                      OdsayProperties properties,
                      Clock clock) {
        this.odsayClient = odsayClient;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public boolean refreshRouteSync(Schedule schedule) {
        try {
            String raw = callOdsay(schedule);
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
            String raw = callOdsay(schedule);
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

    private String callOdsay(Schedule schedule) {
        return odsayClient.searchPubTransPathT(
                bd(schedule.getOriginLng()),
                bd(schedule.getOriginLat()),
                bd(schedule.getDestinationLng()),
                bd(schedule.getDestinationLat())
        );
    }

    private Route mapToRoute(Schedule schedule, String raw) {
        return mapper.toRoute(raw,
                bd(schedule.getOriginLng()), bd(schedule.getOriginLat()),
                bd(schedule.getDestinationLng()), bd(schedule.getDestinationLat()));
    }

    /**
     * 저장된 raw JSON({@code schedule.routeSummaryJson})을 매핑 시도.
     * raw 없음/매핑 실패 시 {@link Optional#empty()} — caller가 다음 fallback으로 진행.
     * <p>cache hit 경로(§6.1)와 ODsay 실패 stale fallback 경로 양쪽에서 동일하게 사용.
     * 매핑 실패가 무시되지 않도록 Optional로 감싸 caller가 명시적으로 검사하게 강제.
     */
    private Optional<Route> tryMapCache(Schedule schedule) {
        String raw = schedule.getRouteSummaryJson();
        if (raw == null) {
            return Optional.empty();
        }
        try {
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
     */
    private void applyToSchedule(Schedule schedule, Route route, String raw) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime recommendedDeparture = schedule.getArrivalTime()
                .minusMinutes(route.totalDurationMinutes());
        schedule.updateRouteInfo(
                route.totalDurationMinutes(),
                recommendedDeparture,
                raw,
                now
        );
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
