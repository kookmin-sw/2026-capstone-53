package com.todayway.backend.map.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link Coordinate} compact constructor 의 NaN/Infinity/range 가드 회귀 검증.
 * 현재는 compile-time 안전망 — query 파라미터는 controller 의 {@code @DecimalMin/@DecimalMax}
 * 가 1차 가드. 본 테스트는 future 회귀 (record 단순화 등) 시 invariant 손실 방지.
 */
class CoordinateTest {

    @Test
    void NaN_lat_거부() {
        assertThatThrownBy(() -> new Coordinate(Double.NaN, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void NaN_lng_거부() {
        assertThatThrownBy(() -> new Coordinate(0, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void positive_infinity_거부() {
        assertThatThrownBy(() -> new Coordinate(Double.POSITIVE_INFINITY, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Coordinate(0, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negative_infinity_거부() {
        assertThatThrownBy(() -> new Coordinate(Double.NEGATIVE_INFINITY, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Coordinate(0, Double.NEGATIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lat_범위_초과_거부() {
        assertThatThrownBy(() -> new Coordinate(90.0001, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Coordinate(-90.0001, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lng_범위_초과_거부() {
        assertThatThrownBy(() -> new Coordinate(0, 180.0001))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Coordinate(0, -180.0001))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 경계값_허용() {
        // WGS-84 / ISO 6709 — 극점/안티자오선은 valid 좌표.
        assertThatCode(() -> new Coordinate(90.0, 180.0)).doesNotThrowAnyException();
        assertThatCode(() -> new Coordinate(-90.0, -180.0)).doesNotThrowAnyException();
        assertThatCode(() -> new Coordinate(0.0, 0.0)).doesNotThrowAnyException();
    }
}
