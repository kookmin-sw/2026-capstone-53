package com.todayway.backend.route;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todayway.backend.external.ExternalApiException;
import com.todayway.backend.external.tmap.TmapClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ODsay {@code searchPubTransPathT} raw JSON → {@link Route} 변환.
 * 명세 §6.1 v1.1.4 매핑표를 결정적으로 적용. 자의적 매핑 금지.
 *
 * <h3>WALK 구간 path 보충 알고리즘</h3>
 * <p>ODsay 응답의 WALK subPath는 좌표 키({@code startX/startY/endX/endY})가 아예 없다.
 * 명세 §11.5의 {@code path: [number, number][]} 필수 필드를 채우기 위해 schedule의
 * 출/도착 좌표 + 이전 transit 끝점으로 보충:
 * <ul>
 *   <li>첫 WALK: {@code origin} → 다음 transit {@code startX/startY}</li>
 *   <li>중간 WALK: 이전 transit {@code endX/endY} → 다음 transit {@code startX/startY}</li>
 *   <li>마지막 WALK: 이전 transit {@code endX/endY} → {@code destination}</li>
 *   <li>all-WALK (transit 0개): 단일 WALK가 {@code origin} → {@code destination} 직선.
 *       ODsay 도보-only 응답이 path 형태로 들어오는 가설적 케이스에 대한 보충 — 실제 운영에선
 *       700m 이내 응답이 에러 코드(코드 -98)로 떨어져 mapper의 {@code path[0] 없음} 분기에서
 *       graceful catch (구현 메모)</li>
 * </ul>
 *
 * <h3>transit 구간 path (v1.1.10)</h3>
 * <p>우선순위: {@code loadLane} 도로 곡선 → {@code passStopList} 직선 fallback.
 * <ul>
 *   <li>1순위: {@code laneRawJson}의 {@code result.lane[i].section[].graphPos[].{x, y}}
 *       (i = transit subPath 인덱스 — ODsay 공식 문서엔 명시 X, 검증된 가정. 길이 mismatch 시 전체 fallback)</li>
 *   <li>2순위 (laneRawJson 누락 / 길이 불일치 / 좌표 sanity 위반 / 파싱 실패):
 *       {@code [startX, startY]} + {@code passStopList.stations[].x/y} + {@code [endX, endY]} 직선
 *       — 명세 §6.1 v1.1.10 비고</li>
 * </ul>
 *
 * <h3>예외 정책</h3>
 * <p>매핑 실패 시 {@link IllegalStateException} (호출자 {@code OdsayRouteService}가 catch).
 * 부분 매핑은 안 함 — 데이터 무결성 우선.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OdsayResponseMapper {

    private final ObjectMapper objectMapper;
    private final TmapClient tmapClient;

    /**
     * @param pathRawJson  {@code searchPubTransPathT} raw 응답
     * @param laneRawJson  {@code loadLane} raw 응답 (nullable). null이거나 매핑 실패 시
     *                     transit segment의 path는 {@code passStopList} 직선으로 fallback —
     *                     명세 §6.1 v1.1.10 비고
     * @param originLng    WALK path 보충용 — schedule.origin_lng
     * @param originLat    schedule.origin_lat
     * @param destLng      schedule.destination_lng
     * @param destLat      schedule.destination_lat
     * @throws IllegalStateException pathRawJson 형식 위반 — (a) JSON 파싱 실패,
     *         (b) {@code result.path[0]} 없음, (c) {@code result.path[0].info} 없음,
     *         (d) transit subPath의 {@code startX/startY/endX/endY} 누락 (transit 자체 좌표 — passStopList 직선 fallback도 이걸 사용),
     *         (e) {@code passStopList.stations[].x/y}의 숫자 파싱 실패.
     *         laneRawJson 손상은 throw하지 않음 (passStopList 직선 fallback — 명세 §6.1 v1.1.10 비고).
     * @throws IllegalArgumentException — (a) ODsay 스펙 외 {@code trafficType}
     *         ({@link SegmentMode#fromOdsayTrafficType(int)}에서 throw),
     *         (b) transit segment 최종 path가 2점 미만 ({@link RouteSegment} record invariant)
     */
    public Route toRoute(String pathRawJson, String laneRawJson,
                         double originLng, double originLat,
                         double destLng, double destLat) {
        JsonNode root = parse(pathRawJson);
        JsonNode path0 = root.path("result").path("path").path(0);
        if (path0.isMissingNode() || path0.isNull()) {
            throw new IllegalStateException("ODsay 응답에 result.path[0]이 없음");
        }
        JsonNode info = path0.path("info");
        if (info.isMissingNode() || !info.isObject()) {
            // info 부재 시 .asInt() default 0이 silent corruption — Route(0,0,0,0,0,_)이라
            // recommendedDeparture = arrivalTime - 0 = arrivalTime → departureAdvice 오판.
            throw new IllegalStateException("ODsay 응답에 result.path[0].info 객체가 없음");
        }

        // ── §6.1 매핑표 — Route 필드 ──
        int totalDurationMinutes = info.path("totalTime").asInt();
        // ODsay totalDistance는 double(예: 8704.0). 명세 응답 예시는 정수(8500m). 정수 변환.
        int totalDistanceMeters = info.path("totalDistance").asInt();
        int totalWalkMeters = info.path("totalWalk").asInt();
        int transferCount = info.path("subwayTransitCount").asInt()
                          + info.path("busTransitCount").asInt();
        int payment = info.path("payment").asInt();

        // ── segments[] — 두 패스 ──
        // 1) transit start 좌표 미리 수집 (WALK가 다음 transit 시작점을 lookahead 가능)
        JsonNode subPathArr = path0.path("subPath");
        List<double[]> transitStarts = collectTransitStarts(subPathArr);

        // loadLane 곡선 좌표 — transit segment 인덱스 순으로 정렬됨.
        // 길이 mismatch / 범위 외 좌표 / 매핑 실패 시 빈 리스트 → 전체 transit이 passStopList 직선으로 fallback.
        // 부분 매핑은 swap 위험(잘못된 노선 곡선이 silent하게 그려짐) 때문에 허용 X.
        List<List<double[]>> lanePaths = parseLanePaths(laneRawJson, transitStarts.size());

        // 2) 순회: WALK는 lastPoint→nextTransitStart, transit은 lane[] 곡선 또는 passStopList
        List<RouteSegment> segments = new ArrayList<>();
        double[] lastPoint = {originLng, originLat};
        int transitIdx = 0;

        for (JsonNode sp : subPathArr) {
            int trafficType = sp.path("trafficType").asInt();
            SegmentMode mode = SegmentMode.fromOdsayTrafficType(trafficType);
            int sectionTime = sp.path("sectionTime").asInt();
            int distance = sp.path("distance").asInt();

            if (mode == SegmentMode.WALK) {
                // 다음 transit 시작점이 있으면 그쪽으로, 없으면 destination으로 (마지막 WALK 또는 all-WALK 경로)
                double[] nextPoint = transitIdx < transitStarts.size()
                        ? transitStarts.get(transitIdx)
                        : new double[]{destLng, destLat};
                List<double[]> walkPath = resolveWalkPath(lastPoint, nextPoint);
                segments.add(buildWalkSegment(sectionTime, distance, walkPath));
                lastPoint = nextPoint;
            } else {
                List<double[]> graphPath = transitIdx < lanePaths.size()
                        ? lanePaths.get(transitIdx)
                        : null;
                RouteSegment seg = buildTransitSegment(mode, sectionTime, distance, sp, graphPath);
                segments.add(seg);
                // lastPoint 갱신: transit 끝점 — 다음 WALK가 이걸 시작점으로 사용
                lastPoint = new double[]{
                        requireCoord(sp, "endX"),
                        requireCoord(sp, "endY")
                };
                transitIdx++;
            }
        }

        return new Route(
                totalDurationMinutes, totalDistanceMeters, totalWalkMeters,
                transferCount, payment, List.copyOf(segments));
    }

    // 서비스 영역 (한국) bounding box — ODsay가 한국 대중교통 특화 API라 응답 좌표는 이 범위 안.
    // 마라도(33.07) / 백령도(124.7) 포함 여유. 글로벌 확장 시 정책 재검토 필요.
    private static final double SERVICE_LNG_MIN = 124.0;
    private static final double SERVICE_LNG_MAX = 132.0;
    private static final double SERVICE_LAT_MIN = 33.0;
    private static final double SERVICE_LAT_MAX = 39.0;

    /**
     * loadLane raw 응답을 transit segment 인덱스 순서의 path 좌표 리스트로 변환.
     * <p>응답 형식: {@code result.lane[i].section[j].graphPos[k].{x, y}} (명세 §6.1 비고).
     * lane[i]가 i번째 transit subPath와 1:1 매칭된다고 가정 — ODsay 공식 문서엔 명시 X,
     * fixture로 검증된 패턴. 길이 mismatch 시 부분 매핑(swap 위험)은 허용 X — 전체 fallback.
     *
     * <h3>Fallback 정책 (전부 graceful — 빈 리스트 반환)</h3>
     * <ul>
     *   <li>null/blank/파싱 실패/{@code result.lane}이 array 아님</li>
     *   <li>lane 길이 ≠ {@code expectedLaneCount} — 부분 매핑은 swap 위험이라 허용 X</li>
     *   <li>graphPos 좌표 sanity 위반 (NaN/Infinity/한국 좌표 범위 밖/단일 점)</li>
     * </ul>
     *
     * @param expectedLaneCount transit subPath 개수와 일치해야 하는 lane[] 길이
     */
    private List<List<double[]>> parseLanePaths(String laneRawJson, int expectedLaneCount) {
        if (laneRawJson == null || laneRawJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = parse(laneRawJson);
            JsonNode laneArr = root.path("result").path("lane");
            if (!laneArr.isArray()) {
                log.warn("ODsay loadLane 응답에 result.lane[] 배열 없음 — passStopList 직선 fallback");
                return List.of();
            }
            if (laneArr.size() != expectedLaneCount) {
                log.warn("ODsay loadLane 길이 불일치 — lane={} transitCount={} (전체 fallback)",
                        laneArr.size(), expectedLaneCount);
                return List.of();
            }
            List<List<double[]>> result = new ArrayList<>();
            for (int i = 0; i < laneArr.size(); i++) {
                JsonNode lane = laneArr.get(i);
                List<double[]> points = new ArrayList<>();
                for (JsonNode section : lane.path("section")) {
                    for (JsonNode pos : section.path("graphPos")) {
                        points.add(requireKoreaCoord(pos));
                    }
                }
                if (points.size() < 2) {
                    log.warn("ODsay loadLane lane[{}] graphPos 점 {}개 — 단일 점/빈 path는 polyline 무의미, fallback",
                            i, points.size());
                    return List.of();
                }
                result.add(List.copyOf(points));
            }
            return result;
        } catch (RuntimeException e) {
            log.warn("ODsay loadLane 매핑 실패 — passStopList 직선 fallback", e);
            return List.of();
        }
    }

    /**
     * graphPos 좌표 sanity 검증 (두 invariant 분리):
     * <ol>
     *   <li>{@link #requireFiniteCoord} — ODsay 응답 자체의 invariant (NaN/Infinity 거부)</li>
     *   <li>{@link #requireServiceArea} — 우리 서비스 영역(한국) 비즈니스 invariant</li>
     * </ol>
     * silent 통과 시 polyline이 적도/대서양으로 점프하는 시각적 버그.
     */
    private static double[] requireKoreaCoord(JsonNode pos) {
        double[] coord = requireFiniteCoord(pos);
        requireServiceArea(coord);
        return coord;
    }

    /** ODsay 응답 invariant — graphPos는 항상 유한한 숫자. */
    private static double[] requireFiniteCoord(JsonNode pos) {
        double lng = parseCoord(pos.path("x"));
        double lat = parseCoord(pos.path("y"));
        if (!Double.isFinite(lng) || !Double.isFinite(lat)) {
            throw new IllegalStateException(
                    "ODsay graphPos 좌표 NaN/Infinity: lng=" + lng + " lat=" + lat);
        }
        return new double[]{lng, lat};
    }

    /** 비즈니스 invariant — 우리 서비스 영역(한국) 안에 있어야 한다. 글로벌 확장 시 정책 재검토. */
    private static void requireServiceArea(double[] coord) {
        double lng = coord[0];
        double lat = coord[1];
        if (lng < SERVICE_LNG_MIN || lng > SERVICE_LNG_MAX
                || lat < SERVICE_LAT_MIN || lat > SERVICE_LAT_MAX) {
            throw new IllegalStateException(
                    "graphPos 좌표 서비스 영역 밖: lng=" + lng + " lat=" + lat);
        }
    }

    // ── private helpers ──

    private JsonNode parse(String rawJson) {
        try {
            return objectMapper.readTree(rawJson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("ODsay 응답 JSON 파싱 실패", e);
        }
    }

    private static List<double[]> collectTransitStarts(JsonNode subPathArr) {
        List<double[]> starts = new ArrayList<>();
        for (JsonNode sp : subPathArr) {
            int trafficType = sp.path("trafficType").asInt();
            // unknown trafficType은 여기서 IllegalArgumentException — 메인 루프 가기 전에 빠르게 실패
            SegmentMode mode = SegmentMode.fromOdsayTrafficType(trafficType);
            if (mode != SegmentMode.WALK) {
                starts.add(new double[]{
                        requireCoord(sp, "startX"),
                        requireCoord(sp, "startY")
                });
            }
        }
        return starts;
    }

    private static RouteSegment buildWalkSegment(int sectionTime, int distance, List<double[]> path) {
        // WALK는 lineName/lineId/stationStart/stationEnd/stationCount 모두 null
        // (RouteSegment의 @JsonInclude(NON_NULL)이 응답 직렬화 시 키 자체 제거)
        return new RouteSegment(
                SegmentMode.WALK, sectionTime, distance,
                null, null,            // from/to (정류장명) — WALK엔 의미 없음
                null, null,            // lineName/lineId
                null, null, null,      // stationStart/stationEnd/stationCount
                path
        );
    }

    /**
     * 명세 §6.1 v1.1.21 — WALK 구간 path 결정.
     * <ol>
     *   <li>{@link TmapClient#routesPedestrian} 호출 → GeoJSON LineString features 의 좌표 평탄화</li>
     *   <li>실패 (키 미설정 / 401/403/timeout/5xx / 응답 형식 위반) → v1.1.9 합성 직선 fallback</li>
     * </ol>
     * 성공 시 양 끝에 from/to 강제 prepend/append — 관측 기반 보정 (TMAP 이 가까운 보행 도로
     * 진입점으로 snap 하는 경우 응답 양 끝이 from/to 와 미세 차이 가능). transit 정류장 좌표와
     * 시각상 정확히 만나도록 보장.
     */
    private List<double[]> resolveWalkPath(double[] from, double[] to) {
        List<double[]> fallback = List.of(
                new double[]{from[0], from[1]},
                new double[]{to[0], to[1]}
        );
        if (!tmapClient.isConfigured()) {
            return fallback;
        }
        try {
            String raw = tmapClient.routesPedestrian(from[0], from[1], to[0], to[1]);
            List<double[]> coords = parseTmapLineString(raw);
            if (coords.size() < 2) {
                // L1 — features 누락 / LineString 0개 / 좌표 invariant 모두 skip 의 합성 결과. 응답 형식
                // 검증 필요한 신호로 1회 log (운영 INFO 안 보이지만 fallback 빈도 추적은 L2 백로그).
                log.debug("TMAP 응답 LineString 좌표 < 2 — graceful fallback 직선");
                return fallback;
            }
            // 양 끝 강제 prepend/append — 정류장 좌표 정확 매칭.
            List<double[]> result = new ArrayList<>(coords.size() + 2);
            result.add(new double[]{from[0], from[1]});
            result.addAll(coords);
            result.add(new double[]{to[0], to[1]});
            return result;
        } catch (ExternalApiException e) {
            log.debug("TMAP WALK 호출 실패 — graceful fallback 직선: type={} status={}",
                    e.getType(), e.getHttpStatus());
            return fallback;
        } catch (RuntimeException e) {
            log.debug("TMAP 응답 파싱 실패 — graceful fallback 직선: cause={}",
                    e.getClass().getSimpleName());
            return fallback;
        }
    }

    /**
     * TMAP GeoJSON FeatureCollection → LineString features 의 coordinates 평탄화. graceful —
     * 손상된 좌표는 silent skip (전체 fallback 으로 떨어지지 않음, 정상 좌표만 살림). 모든 좌표가
     * skip 되면 caller(resolveWalkPath)의 {@code coords.size() < 2} 가드가 fallback 트리거.
     *
     * <p>좌표 단위 invariant:
     * <ol>
     *   <li>{@link JsonNode#isNumber} — non-numeric (string/object/null) silent {@code 0.0}
     *       반환 차단 → 좌표 (0,0) 적도/대서양 점프 방지.</li>
     *   <li>{@link Double#isFinite} — NaN/Infinity 차단.</li>
     *   <li>한국 service area bbox — transit graphPos 와 같은 invariant. 외국 좌표 silent 통과 차단
     *       (TMAP 한국 전용 서비스라 기대값 0).</li>
     * </ol>
     */
    private List<double[]> parseTmapLineString(String rawJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (JsonProcessingException e) {
            // M1 — TMAP 호출 흐름 별도 라벨링. ODsay 의 parse() 와 메시지 구별 (운영 디버깅 시 어느
            // 외부 API 회귀인지 즉시 식별).
            throw new IllegalStateException("TMAP 응답 JSON 파싱 실패", e);
        }
        JsonNode features = root.path("features");
        if (!features.isArray()) {
            return List.of();
        }
        List<double[]> coords = new ArrayList<>();
        for (JsonNode f : features) {
            if (!"LineString".equals(f.path("geometry").path("type").asText())) {
                continue;
            }
            for (JsonNode pt : f.path("geometry").path("coordinates")) {
                if (!pt.isArray() || pt.size() < 2) continue;
                JsonNode lngNode = pt.get(0);
                JsonNode latNode = pt.get(1);
                if (!lngNode.isNumber() || !latNode.isNumber()) continue;
                double lng = lngNode.asDouble();
                double lat = latNode.asDouble();
                if (!Double.isFinite(lng) || !Double.isFinite(lat)) continue;
                if (lng < SERVICE_LNG_MIN || lng > SERVICE_LNG_MAX
                        || lat < SERVICE_LAT_MIN || lat > SERVICE_LAT_MAX) continue;
                coords.add(new double[]{lng, lat});
            }
        }
        return coords;
    }

    private static RouteSegment buildTransitSegment(SegmentMode mode, int sectionTime,
                                                    int distance, JsonNode sp,
                                                    List<double[]> graphPath) {
        String startName = textOrNull(sp.path("startName"));
        String endName = textOrNull(sp.path("endName"));
        Integer stationCount = sp.has("stationCount") ? sp.path("stationCount").asInt() : null;

        // mode별 lineName/lineId 분기 (§6.1 매핑표)
        JsonNode lane0 = sp.path("lane").path(0);
        String lineName;
        String lineId;
        if (mode == SegmentMode.SUBWAY) {
            lineName = textOrNull(lane0.path("name"));
            lineId = numberOrTextOrNull(lane0.path("subwayCode"));
        } else {  // BUS
            lineName = textOrNull(lane0.path("busNo"));
            lineId = numberOrTextOrNull(lane0.path("busID"));
        }

        // SUBWAY는 stationStart/stationEnd 채움, BUS는 from/to 중복이라 null (§6.1 매핑표)
        String stationStart = mode == SegmentMode.SUBWAY ? startName : null;
        String stationEnd = mode == SegmentMode.SUBWAY ? endName : null;

        // path 결정 (§6.1 v1.1.10):
        //   - graphPath(loadLane.lane[i].section[].graphPos[]) 있으면 → 도로 곡선
        //   - 없거나 비었으면 → passStopList 직선 fallback (startX/Y + stations[].x/y + endX/Y)
        // size < 2 검증은 RouteSegment record compact ctor에서 IllegalArgumentException throw.
        List<double[]> path = (graphPath != null && !graphPath.isEmpty())
                ? graphPath
                : buildPassStopListPath(sp);

        return new RouteSegment(
                mode, sectionTime, distance,
                startName, endName,
                lineName, lineId,
                stationStart, stationEnd, stationCount,
                path
        );
    }

    private static List<double[]> buildPassStopListPath(JsonNode sp) {
        List<double[]> path = new ArrayList<>();
        path.add(new double[]{requireCoord(sp, "startX"), requireCoord(sp, "startY")});
        for (JsonNode st : sp.path("passStopList").path("stations")) {
            path.add(new double[]{
                    parseCoord(st.path("x")),
                    parseCoord(st.path("y"))
            });
        }
        path.add(new double[]{requireCoord(sp, "endX"), requireCoord(sp, "endY")});
        return List.copyOf(path);
    }

    private static String textOrNull(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return null;
        String text = node.asText();
        return text.isEmpty() ? null : text;
    }

    /**
     * ODsay {@code lane[0].subwayCode}/{@code busID}는 응답에 따라 number 또는 string으로 옴.
     * 명세 §11.5 {@code lineId: string}에 맞춰 통일.
     */
    private static String numberOrTextOrNull(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return null;
        if (node.isNumber()) return String.valueOf(node.asLong());
        String text = node.asText();
        return text.isEmpty() ? null : text;
    }

    /**
     * {@code passStopList.stations[].x/y}는 ODsay 응답상 string ("126.994769")으로 옴.
     * 안전 차원에서 number도 처리. missing/null/파싱 실패 모두 {@link IllegalStateException} —
     * silent {@code 0.0} 반환은 좌표 (0,0)이 path에 섞여 polyline이 대서양으로 점프하는 시각적 버그.
     */
    private static double parseCoord(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            throw new IllegalStateException("ODsay 좌표 누락 (missing/null)");
        }
        if (node.isNumber()) return node.asDouble();
        try {
            return Double.parseDouble(node.asText());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("ODsay 좌표 파싱 실패: " + node.asText(), e);
        }
    }

    /**
     * transit subPath의 필수 좌표 키({@code startX/startY/endX/endY}) 추출. 누락 시 {@link IllegalStateException}.
     * <p>{@code JsonNode.asDouble()}은 missing 노드에 대해 default {@code 0.0}을 반환하는데, (0,0) 좌표가
     * path에 섞이면 silent corruption이라 명시 검증.
     */
    private static double requireCoord(JsonNode parent, String key) {
        JsonNode node = parent.path(key);
        if (node.isMissingNode() || node.isNull()) {
            throw new IllegalStateException("ODsay transit 좌표 누락: " + key);
        }
        return node.asDouble();
    }
}
