package com.todayway.backend.route;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
 * </ul>
 *
 * <h3>transit 구간 path</h3>
 * <p>{@code [startX, startY]} + {@code passStopList.stations[].x/y} + {@code [endX, endY]} 직선 연결.
 * 정확한 도로 곡선이 필요하면 ODsay {@code loadLane} 추가 호출이지만 MVP 범위 외 — 명세 §6.1
 * v1.1.4 비고 *"passStopList 좌표 직선 허용"*.
 *
 * <h3>예외 정책</h3>
 * <p>매핑 실패 시 {@link IllegalStateException} (호출자 {@code OdsayRouteService}가 catch).
 * 부분 매핑은 안 함 — 데이터 무결성 우선.
 */
@Component
@RequiredArgsConstructor
public class OdsayResponseMapper {

    private final ObjectMapper objectMapper;

    /**
     * @param rawJson    ODsay raw 응답 (전체 트리)
     * @param originLng  WALK path 보충용 — schedule.origin_lng
     * @param originLat  schedule.origin_lat
     * @param destLng    schedule.destination_lng
     * @param destLat    schedule.destination_lat
     * @throws IllegalStateException 응답 형식 위반 ({@code path[0]} 없음, 좌표 파싱 실패 등)
     * @throws IllegalArgumentException ODsay 스펙 외 {@code trafficType}
     *         (예: 4=택시 추가 등 — {@link SegmentMode#fromOdsayTrafficType(int)}에서 throw)
     */
    public Route toRoute(String rawJson,
                         double originLng, double originLat,
                         double destLng, double destLat) {
        JsonNode root = parse(rawJson);
        JsonNode path0 = root.path("result").path("path").path(0);
        if (path0.isMissingNode() || path0.isNull()) {
            throw new IllegalStateException("ODsay 응답에 result.path[0]이 없음");
        }
        JsonNode info = path0.path("info");

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

        // 2) 순회: WALK는 lastPoint→nextTransitStart, transit은 station 좌표
        List<RouteSegment> segments = new ArrayList<>();
        double[] lastPoint = {originLng, originLat};
        int transitIdx = 0;

        for (JsonNode sp : subPathArr) {
            int trafficType = sp.path("trafficType").asInt();
            SegmentMode mode = SegmentMode.fromOdsayTrafficType(trafficType);
            int sectionTime = sp.path("sectionTime").asInt();
            int distance = sp.path("distance").asInt();

            if (mode == SegmentMode.WALK) {
                segments.add(buildWalkSegment(
                        sectionTime, distance, lastPoint,
                        transitIdx < transitStarts.size()
                                ? transitStarts.get(transitIdx)
                                : new double[]{destLng, destLat}));
                lastPoint = transitIdx < transitStarts.size()
                        ? transitStarts.get(transitIdx)
                        : new double[]{destLng, destLat};
            } else {
                RouteSegment seg = buildTransitSegment(mode, sectionTime, distance, sp);
                segments.add(seg);
                // lastPoint 갱신: transit 끝점 — 다음 WALK가 이걸 시작점으로 사용
                lastPoint = new double[]{
                        sp.path("endX").asDouble(),
                        sp.path("endY").asDouble()
                };
                transitIdx++;
            }
        }

        return new Route(
                totalDurationMinutes, totalDistanceMeters, totalWalkMeters,
                transferCount, payment, List.copyOf(segments));
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
            if (trafficType != 3) {  // WALK 아님 = transit
                starts.add(new double[]{
                        sp.path("startX").asDouble(),
                        sp.path("startY").asDouble()
                });
            }
        }
        return starts;
    }

    private static RouteSegment buildWalkSegment(int sectionTime, int distance,
                                                 double[] from, double[] to) {
        // WALK는 lineName/lineId/stationStart/stationEnd/stationCount 모두 null
        // (RouteSegment의 @JsonInclude(NON_NULL)이 응답 직렬화 시 키 자체 제거)
        return new RouteSegment(
                SegmentMode.WALK, sectionTime, distance,
                null, null,            // from/to (정류장명) — WALK엔 의미 없음
                null, null,            // lineName/lineId
                null, null, null,      // stationStart/stationEnd/stationCount
                List.of(
                        new double[]{from[0], from[1]},
                        new double[]{to[0], to[1]}
                )
        );
    }

    private static RouteSegment buildTransitSegment(SegmentMode mode, int sectionTime,
                                                    int distance, JsonNode sp) {
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

        // path: startX/Y + passStopList.stations[].x/y + endX/Y 직선
        List<double[]> path = new ArrayList<>();
        path.add(new double[]{sp.path("startX").asDouble(), sp.path("startY").asDouble()});
        for (JsonNode st : sp.path("passStopList").path("stations")) {
            path.add(new double[]{
                    parseCoord(st.path("x")),
                    parseCoord(st.path("y"))
            });
        }
        path.add(new double[]{sp.path("endX").asDouble(), sp.path("endY").asDouble()});

        return new RouteSegment(
                mode, sectionTime, distance,
                startName, endName,
                lineName, lineId,
                stationStart, stationEnd, stationCount,
                List.copyOf(path)
        );
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
     * 안전 차원에서 number도 처리. 파싱 실패 시 {@link IllegalStateException}.
     */
    private static double parseCoord(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return 0.0;
        if (node.isNumber()) return node.asDouble();
        try {
            return Double.parseDouble(node.asText());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("ODsay 좌표 파싱 실패: " + node.asText(), e);
        }
    }
}
