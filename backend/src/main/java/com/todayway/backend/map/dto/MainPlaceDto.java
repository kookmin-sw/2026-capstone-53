package com.todayway.backend.map.dto;

/**
 * 명세 §4.1 — {@code nearestSchedule.origin / destination} 의 응답 형태.
 * 명세 §11.1 {@code Place} 의 sub-shape (name + 좌표만 노출).
 */
public record MainPlaceDto(String name, double lat, double lng) {
}
