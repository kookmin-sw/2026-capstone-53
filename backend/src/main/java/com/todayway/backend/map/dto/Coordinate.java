package com.todayway.backend.map.dto;

/**
 * 명세 §4 응답의 위경도 좌표 ({@code mapCenter}, {@code defaultCenter}).
 *
 * <p>compact constructor 가 NaN/Infinity/range 를 거부 — query 파라미터의 비정상 값이 silent 로
 * 흘러가지 않게 한다.
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
