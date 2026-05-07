package com.todayway.backend.geocode.domain;

/**
 * V1__init.sql {@code geocode_cache.provider} ENUM. 명세 §8.1 / §11.1 정합.
 *
 * <p>{@link #KAKAO_LOCAL} — Kakao Local API 응답을 그대로 캐시 (현 MVP 사용).
 * <p>{@link #NAVER} — NAVER Geocoding 도입 시 (P1 예정).
 *
 * <p>주의: 명세 §11.1 {@code Place.provider} 는 도메인 추상화 ENUM
 * ({@code NAVER/KAKAO/ODSAY/MANUAL}) 이고, schedule 저장 시 {@code KAKAO_LOCAL → KAKAO} 변환
 * 필요 (명세 §8.1 v1.1.4 변환표). 본 enum 은 캐시 키 구분용으로만 사용.
 */
public enum GeocodeCacheProvider {
    NAVER,
    KAKAO_LOCAL
}
