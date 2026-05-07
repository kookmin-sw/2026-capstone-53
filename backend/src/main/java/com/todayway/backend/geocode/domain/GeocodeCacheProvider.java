package com.todayway.backend.geocode.domain;

/**
 * V1__init.sql {@code geocode_cache.provider} ENUM. 명세 §8.1 / §11.1 정합.
 *
 * <p>{@link #KAKAO_LOCAL} — Kakao Local API 응답을 그대로 캐시 (MVP 사용).
 * <p>{@link #NAVER} — V1__init.sql ENUM 정합. NAVER Geocoding 도입 시 활성화.
 *
 * <p>주의: 명세 §11.1 {@code Place.provider} 는 도메인 추상화 ENUM
 * ({@code NAVER/KAKAO/ODSAY/MANUAL}) — schedule 저장 payload 는 {@code KAKAO} 로 들어와야 한다는
 * invariant. 변환 함수 sample 은 명세 §8.1 v1.1.4 에 박혀있고 호출 위치 (frontend / backend) 는
 * 명세 silent — 본 enum 은 캐시 키 구분용으로만 사용.
 */
public enum GeocodeCacheProvider {
    NAVER,
    KAKAO_LOCAL
}
