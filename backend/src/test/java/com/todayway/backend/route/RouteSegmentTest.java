package com.todayway.backend.route;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RouteSegment} record invariant — path는 polyline 의미상 2점 이상 필수 (명세 §11.5).
 * <p>v1.1.10에서 mapper에 박혀있던 검증을 record compact ctor로 이동 — caller(mapper/외부)
 * 무관하게 invariant 보장. WALK/SUBWAY/BUS 모두 동일 적용.
 */
class RouteSegmentTest {

    @Test
    void path_null이면_IllegalArgumentException() {
        assertThatThrownBy(() -> new RouteSegment(
                SegmentMode.WALK, 5, 350, "A", "B",
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

    @Test
    void path가_2점이면_정상_생성() {
        RouteSegment seg = new RouteSegment(
                SegmentMode.WALK, 5, 350, "A", "B",
                null, null, null, null, null,
                List.of(new double[]{127.0, 37.6}, new double[]{127.1, 37.5}));
        // 정상 — 2점이라 invariant 통과
        org.assertj.core.api.Assertions.assertThat(seg.path()).hasSize(2);
    }
}
