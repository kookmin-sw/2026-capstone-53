package com.todayway.backend.map.dto;

/**
 * 명세 §4 응답에 등장하는 위경도 좌표 (예: {@code mapCenter}, {@code defaultCenter}).
 *
 * <p>compact constructor 가 NaN/Infinity/range 를 거부 — {@code Coordinate(0,0)} 같은 silent
 * "Null Island" 좌표는 명세상 valid 범위 안이라 통과시키되, 그 외 비정상 값은 즉시 차단해
 * 클라이언트가 의도한 좌표를 보내지 않은 케이스가 silent corruption 으로 흘러가지 않도록 한다.
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
