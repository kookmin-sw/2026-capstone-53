package com.todayway.backend.schedule.domain;

/**
 * 장소 정보의 출처 (origin_provider / destination_provider). 명세 §11.1 / V1__init.sql 정합.
 *
 * <p>본 ENUM 은 도메인 추상화 — schedule 도메인에서만 사용. 명세 §8.1 v1.1.4 변환표에 따라 geocode
 * 응답의 {@link com.todayway.backend.geocode.domain.GeocodeCacheProvider#KAKAO_LOCAL} 는 schedule
 * 저장 시 {@link #KAKAO} 로 변환되어 들어온다 (변환 책임은 frontend).
 */
public enum PlaceProvider {
    NAVER, KAKAO, ODSAY, MANUAL
}
