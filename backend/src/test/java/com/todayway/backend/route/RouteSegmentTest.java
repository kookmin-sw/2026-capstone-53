package com.todayway.backend.route;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RouteSegment} record invariant — 명세 §11.5 / §6.1 매핑표 정합:
 * <ul>
 *   <li>{@code path}: 2점 이상 (polyline 의미)</li>
 *   <li>{@code mode}: non-null</li>
 *   <li>mode-specific nullable 매트릭스 — caller(mapper/외부) 무관하게 record가 강제</li>
 * </ul>
 */
class RouteSegmentTest {

    private static final List<double[]> VALID_PATH =
            List.of(new double[]{127.0, 37.6}, new double[]{127.1, 37.5});

    // ─── path size invariant ─────────────────────────────────────

    @Test
    void path_null이면_IllegalArgumentException() {
        assertThatThrownBy(() -> new RouteSegment(
                SegmentMode.WALK, 5, 350, null, null,
                null, null, null, null, null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2점 이상");
    }

    @Test
    void path가_단일_점이면_IllegalArgumentException() {
        assertThatThrownBy(() -> new RouteSegment(
                SegmentMode.BUS, 10, 1000, "A", "B",
                "100", "1", null, null, 5,
                List.of(new double[]{127.0, 37.6})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2점 이상");
    }

    // ─── mode-specific nullable 매트릭스 ──────────────────────────

    @Test
    void WALK_정상() {
        // WALK는 from/to/line*/station* 5+2 = 7필드 모두 null
        RouteSegment seg = new RouteSegment(
                SegmentMode.WALK, 5, 350, null, null,
                null, null, null, null, null,
                VALID_PATH);
        assertThat(seg.mode()).isEqualTo(SegmentMode.WALK);
    }

    @Test
    void WALK에_lineName_있으면_IAE() {
        assertThatThrownBy(() -> new RouteSegment(
                SegmentMode.WALK, 5, 350, null, null,
                "잘못", null, null, null, null,
                VALID_PATH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WALK");
    }

    @Test
    void WALK에_from_있으면_IAE() {
        assertThatThrownBy(() -> new RouteSegment(
                SegmentMode.WALK, 5, 350, "잘못", null,
                null, null, null, null, null,
                VALID_PATH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WALK");
    }

    @Test
    void SUBWAY_정상() {
        // SUBWAY는 lineName/lineId/stationStart/stationEnd/stationCount 모두 채움
        RouteSegment seg = new RouteSegment(
                SegmentMode.SUBWAY, 25, 7500, "우이동역", "성신여대입구역",
                "우이신설선", "109", "우이동역", "성신여대입구역", 7,
                VALID_PATH);
        assertThat(seg.mode()).isEqualTo(SegmentMode.SUBWAY);
    }

    @Test
    void SUBWAY에_stationCount_누락이면_IAE() {
        assertThatThrownBy(() -> new RouteSegment(
                SegmentMode.SUBWAY, 25, 7500, "A", "B",
                "1호선", "1", "A", "B", null,  // stationCount null
                VALID_PATH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SUBWAY");
    }

    @Test
    void SUBWAY에_lineName_누락이면_IAE() {
        assertThatThrownBy(() -> new RouteSegment(
                SegmentMode.SUBWAY, 25, 7500, "A", "B",
                null, "1", "A", "B", 5,  // lineName null
                VALID_PATH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SUBWAY");
    }

    @Test
    void BUS_정상() {
        // BUS는 lineName/lineId 채움, stationStart/stationEnd null (from/to와 중복 회피)
        RouteSegment seg = new RouteSegment(
                SegmentMode.BUS, 29, 8385, "국민대학교앞", "시청앞.덕수궁",
                "1711", "908", null, null, 15,
                VALID_PATH);
        assertThat(seg.mode()).isEqualTo(SegmentMode.BUS);
    }

    @Test
    void BUS에_stationStart_있으면_IAE() {
        // BUS는 stationStart null이어야 함 (from과 중복 회피, §6.1 매핑표)
        assertThatThrownBy(() -> new RouteSegment(
                SegmentMode.BUS, 29, 8385, "A", "B",
                "100", "1", "A", null, 15,  // stationStart 잘못 채움
                VALID_PATH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BUS");
    }

    @Test
    void BUS에_lineName_누락이면_IAE() {
        assertThatThrownBy(() -> new RouteSegment(
                SegmentMode.BUS, 29, 8385, "A", "B",
                null, "1", null, null, 15,  // lineName null
                VALID_PATH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BUS");
    }

    @Test
    void mode_null이면_IAE() {
        assertThatThrownBy(() -> new RouteSegment(
                null, 5, 350, null, null, null, null, null, null, null,
                VALID_PATH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mode");
    }
}
