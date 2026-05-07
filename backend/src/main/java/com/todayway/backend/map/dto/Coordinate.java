package com.todayway.backend.map.dto;

/**
 * 명세 §4 응답의 위경도 좌표 ({@code mapCenter}, {@code defaultCenter}).
 *
 * <p>compact constructor 가 NaN/Infinity/range 를 거부 — query 파라미터의 비정상 값이 silent 로
 * 흘러가지 않게 한다. {@code (0, 0)} 은 valid 범위 안이라 통과되지만 운영 측면에선 "Null Island"
 * 사고 신호일 수 있다 (호출자가 좌표를 셋업 안 한 채 호출).
 */
public record Coordinate(double lat, double lng) {

    public Coordinate {
        if (Double.isNaN(lat) || Double.isInfinite(lat) || lat < -90 || lat > 90) {
            throw new IllegalArgumentException("lat 은 [-90, 90] 범위의 유한 값이어야 함: " + lat);
        }
        if (Double.isNaN(lng) || Double.isInfinite(lng) || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("lng 은 [-180, 180] 범위의 유한 값이어야 함: " + lng);
        }
    }
}
