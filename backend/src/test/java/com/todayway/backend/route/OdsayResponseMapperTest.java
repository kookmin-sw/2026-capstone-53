package com.todayway.backend.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link OdsayResponseMapper} 단위 테스트.
 *
 * <p>실 ODsay 응답 fixture 기반 (국민대→서울시청, 1711번 버스 1회 환승).
 * 명세 §6.1 v1.1.4 매핑표를 한 줄씩 검증 — ODsay 스펙이나 우리 mapper가 변경되면 회귀 잡음.
 */
class OdsayResponseMapperTest {

    private static final String FIXTURE_PATH = "src/test/resources/fixtures/odsay-kookmin-to-cityhall.json";

    // 국민대→서울시청 좌표 (WALK path 보충 검증용)
    private static final double ORIGIN_LNG = 126.997;
    private static final double ORIGIN_LAT = 37.611;
    private static final double DEST_LNG = 126.978;
    private static final double DEST_LAT = 37.5665;

    private OdsayResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new OdsayResponseMapper(new ObjectMapper());
    }

    @Test
    void 실제_ODsay_응답_매핑_명세_6_1_매핑표_전체_검증() throws IOException {
        String raw = Files.readString(Path.of(FIXTURE_PATH));

        Route route = mapper.toRoute(raw, null, ORIGIN_LNG, ORIGIN_LAT, DEST_LNG, DEST_LAT);

        // ── Route 필드 (§6.1 매핑표) ──
        assertThat(route.totalDurationMinutes()).isEqualTo(34);    // info.totalTime
        assertThat(route.totalDistanceMeters()).isEqualTo(8704);   // info.totalDistance (8704.0 → int)
        assertThat(route.totalWalkMeters()).isEqualTo(319);        // info.totalWalk
        assertThat(route.transferCount()).isEqualTo(1);            // subway 0 + bus 1
        assertThat(route.payment()).isEqualTo(1500);               // info.payment
        assertThat(route.segments()).hasSize(3);                   // WALK → BUS → WALK
    }

    @Test
    void WALK_구간_매핑_origin과_destination_좌표로_path_보충() throws IOException {
        String raw = Files.readString(Path.of(FIXTURE_PATH));

        Route route = mapper.toRoute(raw, null, ORIGIN_LNG, ORIGIN_LAT, DEST_LNG, DEST_LAT);

        // [0] 첫 WALK: origin → 다음 transit(BUS) startX/Y
        RouteSegment seg0 = route.segments().get(0);
        assertThat(seg0.mode()).isEqualTo(SegmentMode.WALK);
        assertThat(seg0.durationMinutes()).isEqualTo(3);
        assertThat(seg0.distanceMeters()).isEqualTo(199);
        // §6.1 — WALK는 from/to/lineName/lineId/stationStart/stationEnd/stationCount 모두 null
        assertThat(seg0.from()).isNull();
        assertThat(seg0.to()).isNull();
        assertThat(seg0.lineName()).isNull();
        assertThat(seg0.lineId()).isNull();
        assertThat(seg0.stationStart()).isNull();
        assertThat(seg0.stationEnd()).isNull();
        assertThat(seg0.stationCount()).isNull();
        // path: origin → 국민대학교앞 (BUS startX/Y)
        assertThat(seg0.path()).hasSize(2);
        assertThat(seg0.path().get(0)).containsExactly(ORIGIN_LNG, ORIGIN_LAT);
        assertThat(seg0.path().get(1)).containsExactly(126.994769, 37.61072);

        // [2] 마지막 WALK: 이전 transit(BUS) endX/Y → destination
        RouteSegment seg2 = route.segments().get(2);
        assertThat(seg2.mode()).isEqualTo(SegmentMode.WALK);
        assertThat(seg2.durationMinutes()).isEqualTo(2);
        assertThat(seg2.distanceMeters()).isEqualTo(120);
        assertThat(seg2.path()).hasSize(2);
        // 첫 점: 이전 transit (BUS) endX/Y — 시청앞.덕수궁 정류장 좌표
        // 끝 점: destination
        assertThat(seg2.path().get(0)).containsExactly(126.976851, 37.565929);
        assertThat(seg2.path().get(1)).containsExactly(DEST_LNG, DEST_LAT);
    }

    @Test
    void all_WALK_응답_단일_WALK가_origin_destination_직선() {
        // ODsay 도보만 가능한 case (700m 이상이지만 대중교통 비효율 등) — transit 0개.
        // §6.1 비고 — origin → destination 직선으로 자연스럽게 처리.
        String raw = """
                {
                  "result": {
                    "path": [{
                      "info": {
                        "totalTime": 12, "totalDistance": 800, "totalWalk": 800,
                        "subwayTransitCount": 0, "busTransitCount": 0, "payment": 0
                      },
                      "subPath": [
                        {"trafficType": 3, "sectionTime": 12, "distance": 800}
                      ]
                    }]
                  }
                }
                """;

        Route route = mapper.toRoute(raw, null, ORIGIN_LNG, ORIGIN_LAT, DEST_LNG, DEST_LAT);

        assertThat(route.transferCount()).isZero();
        assertThat(route.payment()).isZero();
        assertThat(route.segments()).hasSize(1);
        RouteSegment seg = route.segments().get(0);
        assertThat(seg.mode()).isEqualTo(SegmentMode.WALK);
        assertThat(seg.path()).hasSize(2);
        assertThat(seg.path().get(0)).containsExactly(ORIGIN_LNG, ORIGIN_LAT);
        assertThat(seg.path().get(1)).containsExactly(DEST_LNG, DEST_LAT);
    }

    @Test
    void passStopList_좌표_누락이면_IllegalStateException_silent_0_0_corruption_방지() {
        // BUS subPath의 passStopList.stations[]에서 x/y 누락 시 silent 0.0 반환 X → throw.
        // 좌표 (0,0)이 path에 섞이면 polyline이 대서양으로 점프하는 시각적 버그라 명시 검증.
        String raw = """
                {
                  "result": {
                    "path": [{
                      "info": {
                        "totalTime": 10, "totalDistance": 1000, "totalWalk": 0,
                        "subwayTransitCount": 0, "busTransitCount": 1, "payment": 1500
                      },
                      "subPath": [{
                        "trafficType": 2,
                        "sectionTime": 10, "distance": 1000,
                        "startName": "A", "endName": "B", "stationCount": 1,
                        "startX": 127.0, "startY": 37.6,
                        "endX": 127.1, "endY": 37.5,
                        "lane": [{"busNo": "100", "busID": 1}],
                        "passStopList": {"stations": [{"x": null, "y": "37.55"}]}
                      }]
                    }]
                  }
                }
                """;

        assertThatThrownBy(() -> mapper.toRoute(raw, null, 0, 0, 0, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("좌표");
    }

    @Test
    void transit_subPath_startX_누락이면_IllegalStateException() {
        // transit 분기의 startX/Y/endX/Y 필수 — JsonNode.asDouble() default 0.0 함정 방지.
        String raw = """
                {
                  "result": {
                    "path": [{
                      "info": {
                        "totalTime": 10, "totalDistance": 1000, "totalWalk": 0,
                        "subwayTransitCount": 0, "busTransitCount": 1, "payment": 1500
                      },
                      "subPath": [{
                        "trafficType": 2,
                        "sectionTime": 10, "distance": 1000,
                        "startName": "A", "endName": "B", "stationCount": 0,
                        "startY": 37.6, "endX": 127.1, "endY": 37.5,
                        "lane": [{"busNo": "100", "busID": 1}],
                        "passStopList": {"stations": []}
                      }]
                    }]
                  }
                }
                """;

        assertThatThrownBy(() -> mapper.toRoute(raw, null, 0, 0, 0, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("startX");
    }

    @Test
    void BUS_구간_매핑_busNo_busID_string_변환_passStopList_path() throws IOException {
        String raw = Files.readString(Path.of(FIXTURE_PATH));

        Route route = mapper.toRoute(raw, null, ORIGIN_LNG, ORIGIN_LAT, DEST_LNG, DEST_LAT);

        // [1] BUS 1711번
        RouteSegment seg = route.segments().get(1);
        assertThat(seg.mode()).isEqualTo(SegmentMode.BUS);
        assertThat(seg.durationMinutes()).isEqualTo(29);
        assertThat(seg.distanceMeters()).isEqualTo(8385);

        // BUS는 from/to에 정류장명, stationStart/stationEnd는 null (§6.1)
        assertThat(seg.from()).isEqualTo("국민대학교앞");
        assertThat(seg.to()).isEqualTo("시청앞.덕수궁");
        assertThat(seg.stationStart()).isNull();
        assertThat(seg.stationEnd()).isNull();

        // lineName ← lane[0].busNo (string), lineId ← lane[0].busID (number → string 변환)
        assertThat(seg.lineName()).isEqualTo("1711");
        assertThat(seg.lineId()).isEqualTo("908");

        assertThat(seg.stationCount()).isEqualTo(15);

        // path: startX/Y + 16 stations + endX/Y = 18점
        assertThat(seg.path()).hasSize(18);
        // 첫 점: BUS startX/Y
        assertThat(seg.path().get(0)).containsExactly(126.994769, 37.61072);
        // 중간 점들: passStopList.stations[].x/y (string → parseDouble)
        // — fixture 검증 시 stations[0].x="126.994769"였음, parsing 정상 동작 가정
    }

    @Test
    void path_배열이_비어있으면_IllegalStateException() {
        String raw = "{ \"result\": { \"path\": [] } }";

        assertThatThrownBy(() -> mapper.toRoute(raw, null, 0, 0, 0, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("path[0]");
    }

    @Test
    void 잘못된_JSON이면_IllegalStateException() {
        String raw = "this-is-not-json";

        assertThatThrownBy(() -> mapper.toRoute(raw, null, 0, 0, 0, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("파싱 실패");
    }

    @Test
    void SUBWAY_구간_매핑_lane0_name_subwayCode_number_string변환_stationStartEnd() {
        // 명세 §6.1 매핑표 SUBWAY 분기 검증 — BUS와 다른 lane[0] 키 + stationStart/End 채움.
        // lane[0].subwayCode는 ODsay에서 number로 옴 → mapper가 string으로 통일해야 함.
        String raw = """
                {
                  "result": {
                    "path": [{
                      "info": {
                        "totalTime": 20, "totalDistance": 5000, "totalWalk": 100,
                        "subwayTransitCount": 1, "busTransitCount": 0, "payment": 1370
                      },
                      "subPath": [{
                        "trafficType": 1,
                        "sectionTime": 18,
                        "distance": 4900,
                        "startName": "정릉",
                        "endName": "시청",
                        "stationCount": 8,
                        "startX": 127.014, "startY": 37.610,
                        "endX": 126.977, "endY": 37.564,
                        "lane": [{
                          "name": "수도권4호선",
                          "subwayCode": 4
                        }],
                        "passStopList": {
                          "stations": [
                            {"x": "127.010", "y": "37.605"},
                            {"x": "126.990", "y": "37.580"}
                          ]
                        }
                      }]
                    }]
                  }
                }
                """;

        Route route = mapper.toRoute(raw, null, 127.014, 37.610, 126.977, 37.564);

        assertThat(route.segments()).hasSize(1);
        RouteSegment seg = route.segments().get(0);
        assertThat(seg.mode()).isEqualTo(SegmentMode.SUBWAY);
        assertThat(seg.durationMinutes()).isEqualTo(18);
        assertThat(seg.distanceMeters()).isEqualTo(4900);

        // §6.1 — SUBWAY는 lineName ← lane[0].name, lineId ← lane[0].subwayCode (number → string)
        assertThat(seg.lineName()).isEqualTo("수도권4호선");
        assertThat(seg.lineId()).isEqualTo("4");

        // §6.1 — SUBWAY는 stationStart/stationEnd 채움 (BUS와 차이)
        assertThat(seg.stationStart()).isEqualTo("정릉");
        assertThat(seg.stationEnd()).isEqualTo("시청");
        // from/to에도 동일 정류장명 (응답 일관성)
        assertThat(seg.from()).isEqualTo("정릉");
        assertThat(seg.to()).isEqualTo("시청");

        assertThat(seg.stationCount()).isEqualTo(8);

        // path: startX/Y + 2 stations(string→double) + endX/Y = 4점
        assertThat(seg.path()).hasSize(4);
        assertThat(seg.path().get(0)).containsExactly(127.014, 37.610);
        assertThat(seg.path().get(1)).containsExactly(127.010, 37.605);
        assertThat(seg.path().get(2)).containsExactly(126.990, 37.580);
        assertThat(seg.path().get(3)).containsExactly(126.977, 37.564);
    }

    @Test
    void info_객체_누락이면_IllegalStateException_silent_0_corruption_방지() {
        // path[0]은 있지만 info 객체가 빠진 비정상 응답 — .asInt() default 0이 Route(0,0,...) 만들어
        // recommendedDeparture = arrivalTime - 0 으로 오판 발생 가능. 명시 throw.
        String raw = """
                {
                  "result": {
                    "path": [{
                      "subPath": [{"trafficType": 3, "sectionTime": 5, "distance": 100}]
                    }]
                  }
                }
                """;

        assertThatThrownBy(() -> mapper.toRoute(raw, null, 0, 0, 0, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("info");
    }

    @Test
    void loadLane_raw_제공시_transit_path가_graphPos로_교체() throws IOException {
        // §6.1 v1.1.10 — loadLane.lane[i].section[].graphPos[] → transit segment i의 path
        String pathRaw = Files.readString(Path.of(FIXTURE_PATH));
        // fixture는 transit subPath 1개(BUS) → lane[0].section[]만 사용
        String laneRaw = """
                {
                  "result": {
                    "lane": [
                      {
                        "section": [
                          {
                            "graphPos": [
                              {"x": 126.994769, "y": 37.61072},
                              {"x": 126.992, "y": 37.605},
                              {"x": 126.985, "y": 37.580},
                              {"x": 126.980, "y": 37.572},
                              {"x": 126.976851, "y": 37.565929}
                            ]
                          }
                        ]
                      }
                    ]
                  }
                }
                """;

        Route route = mapper.toRoute(pathRaw, laneRaw, ORIGIN_LNG, ORIGIN_LAT, DEST_LNG, DEST_LAT);

        // BUS segment(index 1)의 path가 graphPos 5점으로 교체 — passStopList(18점) 아님
        RouteSegment busSeg = route.segments().get(1);
        assertThat(busSeg.mode()).isEqualTo(SegmentMode.BUS);
        assertThat(busSeg.path()).hasSize(5);
        assertThat(busSeg.path().get(0)).containsExactly(126.994769, 37.61072);
        assertThat(busSeg.path().get(4)).containsExactly(126.976851, 37.565929);
    }

    @Test
    void loadLane_raw_깨졌을때_passStopList_fallback() throws IOException {
        // graceful — lane raw 형식 위반 시 transit path는 기존 passStopList 직선으로 fallback
        String pathRaw = Files.readString(Path.of(FIXTURE_PATH));
        Route route = mapper.toRoute(pathRaw, "this-is-not-json", ORIGIN_LNG, ORIGIN_LAT, DEST_LNG, DEST_LAT);
        assertThat(route.segments().get(1).path()).hasSize(18);
    }

    @Test
    void loadLane_lane_배열_비었을때_passStopList_fallback() throws IOException {
        String pathRaw = Files.readString(Path.of(FIXTURE_PATH));
        Route route = mapper.toRoute(pathRaw, "{ \"result\": { \"lane\": [] } }",
                ORIGIN_LNG, ORIGIN_LAT, DEST_LNG, DEST_LAT);
        assertThat(route.segments().get(1).path()).hasSize(18);
    }

    @Test
    void loadLane_section_여러개면_graphPos_평탄화() {
        // lane[0].section[0,1] 두 section의 graphPos를 합쳐 한 transit segment의 path로
        String pathRaw = """
                {
                  "result": {
                    "path": [{
                      "info": {
                        "totalTime": 30, "totalDistance": 5000, "totalWalk": 0,
                        "subwayTransitCount": 0, "busTransitCount": 1, "payment": 1500
                      },
                      "subPath": [{
                        "trafficType": 2,
                        "sectionTime": 30, "distance": 5000,
                        "startName": "A", "endName": "B", "stationCount": 0,
                        "startX": 127.0, "startY": 37.6,
                        "endX": 127.1, "endY": 37.5,
                        "lane": [{"busNo": "100", "busID": 1}],
                        "passStopList": {"stations": []}
                      }]
                    }]
                  }
                }
                """;
        String laneRaw = """
                {
                  "result": {
                    "lane": [{
                      "section": [
                        {"graphPos": [{"x": 127.0, "y": 37.6}, {"x": 127.05, "y": 37.55}]},
                        {"graphPos": [{"x": 127.05, "y": 37.55}, {"x": 127.1, "y": 37.5}]}
                      ]
                    }]
                  }
                }
                """;

        Route route = mapper.toRoute(pathRaw, laneRaw, 0, 0, 0, 0);

        assertThat(route.segments().get(0).path()).hasSize(4);  // 2 + 2 평탄화
    }

    @Test
    void loadLane_길이_불일치면_swap_위험_방지를_위해_전체_passStopList_fallback() throws IOException {
        // fixture는 transit 1개(BUS). lane 0개 응답이 와도 부분 매핑 X (잘못된 segment에 매핑되어
        // silent하게 다른 노선 곡선이 그려지면 visual 버그라 명세 §6.1 v1.1.10에서 전체 fallback 정책).
        String pathRaw = Files.readString(Path.of(FIXTURE_PATH));
        String laneRaw = "{ \"result\": { \"lane\": [] } }";  // size 0

        Route route = mapper.toRoute(pathRaw, laneRaw, ORIGIN_LNG, ORIGIN_LAT, DEST_LNG, DEST_LAT);

        // BUS path가 passStopList 18점 그대로 (size mismatch → graphPos 무시)
        assertThat(route.segments().get(1).path()).hasSize(18);
    }

    @Test
    void loadLane_graphPos_한국_좌표_범위_밖이면_passStopList_fallback() throws IOException {
        // (0, 0) 같은 좌표가 섞이면 polyline 대서양 점프. silent 통과 차단.
        String pathRaw = Files.readString(Path.of(FIXTURE_PATH));
        String laneRaw = """
                {
                  "result": {
                    "lane": [
                      {
                        "section": [
                          {
                            "graphPos": [
                              {"x": 126.994769, "y": 37.61072},
                              {"x": 0, "y": 0},
                              {"x": 126.976851, "y": 37.565929}
                            ]
                          }
                        ]
                      }
                    ]
                  }
                }
                """;

        Route route = mapper.toRoute(pathRaw, laneRaw, ORIGIN_LNG, ORIGIN_LAT, DEST_LNG, DEST_LAT);

        // 한국 범위 밖 좌표 발견 → 전체 lane drop → passStopList 18점
        assertThat(route.segments().get(1).path()).hasSize(18);
    }

    @Test
    void loadLane_graphPos_NaN이면_passStopList_fallback() throws IOException {
        // ODsay 응답 손상으로 NaN/Infinity가 섞일 가능성 — silent 통과 X
        String pathRaw = Files.readString(Path.of(FIXTURE_PATH));
        // JSON에 NaN은 invalid라 string으로 시뮬레이션 (parseCoord가 NumberFormatException → IllegalStateException)
        String laneRaw = """
                {
                  "result": {
                    "lane": [
                      {
                        "section": [
                          {
                            "graphPos": [
                              {"x": "NaN", "y": "37.61"},
                              {"x": 126.99, "y": 37.61}
                            ]
                          }
                        ]
                      }
                    ]
                  }
                }
                """;

        Route route = mapper.toRoute(pathRaw, laneRaw, ORIGIN_LNG, ORIGIN_LAT, DEST_LNG, DEST_LAT);

        assertThat(route.segments().get(1).path()).hasSize(18);
    }

    @Test
    void loadLane_lane의_graphPos가_2점_미만이면_fallback() throws IOException {
        // polyline은 최소 2점 필요. lane[0]이 1점만 주면 visual 무의미 → fallback.
        String pathRaw = Files.readString(Path.of(FIXTURE_PATH));
        String laneRaw = """
                {
                  "result": {
                    "lane": [
                      {
                        "section": [
                          {"graphPos": [{"x": 126.994769, "y": 37.61072}]}
                        ]
                      }
                    ]
                  }
                }
                """;

        Route route = mapper.toRoute(pathRaw, laneRaw, ORIGIN_LNG, ORIGIN_LAT, DEST_LNG, DEST_LAT);

        assertThat(route.segments().get(1).path()).hasSize(18);
    }

    @Test
    void unknown_trafficType이면_IllegalArgumentException() {
        // ODsay가 명세 외 trafficType (예: 4=택시)을 추가했을 때 silent fallback 안 함.
        // OdsayRouteService에서 명시적 catch → graceful degradation.
        String raw = """
                {
                  "result": {
                    "path": [{
                      "info": {
                        "totalTime": 10, "totalDistance": 100, "totalWalk": 50,
                        "subwayTransitCount": 0, "busTransitCount": 0, "payment": 0
                      },
                      "subPath": [{"trafficType": 99, "sectionTime": 5, "distance": 100}]
                    }]
                  }
                }
                """;

        assertThatThrownBy(() -> mapper.toRoute(raw, null, 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }
}
