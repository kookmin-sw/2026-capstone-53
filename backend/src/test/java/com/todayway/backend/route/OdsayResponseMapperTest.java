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

        Route route = mapper.toRoute(raw, ORIGIN_LNG, ORIGIN_LAT, DEST_LNG, DEST_LAT);

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

        Route route = mapper.toRoute(raw, ORIGIN_LNG, ORIGIN_LAT, DEST_LNG, DEST_LAT);

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
        assertThat(seg2.path().get(1)).containsExactly(DEST_LNG, DEST_LAT);
    }

    @Test
    void BUS_구간_매핑_busNo_busID_string_변환_passStopList_path() throws IOException {
        String raw = Files.readString(Path.of(FIXTURE_PATH));

        Route route = mapper.toRoute(raw, ORIGIN_LNG, ORIGIN_LAT, DEST_LNG, DEST_LAT);

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

        assertThatThrownBy(() -> mapper.toRoute(raw, 0, 0, 0, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("path[0]");
    }

    @Test
    void 잘못된_JSON이면_IllegalStateException() {
        String raw = "this-is-not-json";

        assertThatThrownBy(() -> mapper.toRoute(raw, 0, 0, 0, 0))
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

        Route route = mapper.toRoute(raw, 127.014, 37.610, 126.977, 37.564);

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

        assertThatThrownBy(() -> mapper.toRoute(raw, 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }
}
