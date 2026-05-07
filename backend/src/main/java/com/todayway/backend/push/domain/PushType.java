package com.todayway.backend.push.domain;

/**
 * 명세 §V1 + DB-SQL.txt {@code push_log.push_type} ENUM. MVP는 REMINDER만.
 * 신규 타입 추가 시 V2 마이그레이션으로 컬럼 ENUM 확장 필요.
 */
public enum PushType {
    REMINDER
}
