package com.todayway.backend.schedule.domain;

/**
 * 장소 정보의 출처 (origin_provider / destination_provider).
 * DB ENUM과 정합 (V1__init.sql).
 */
public enum PlaceProvider {
    NAVER, KAKAO, ODSAY, MANUAL
}
