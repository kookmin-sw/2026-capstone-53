package com.todayway.backend.map.dto;

/**
 * 명세 §4 응답에 등장하는 위경도 좌표 (예: {@code mapCenter}, {@code defaultCenter}).
 * 직렬화는 {@code {"lat": ..., "lng": ...}} 형태.
 */
public record Coordinate(double lat, double lng) {
}
