package com.todayway.backend.schedule.domain;

/**
 * 루틴 타입.
 *  - ONCE: 단발성 (default 효과)
 *  - DAILY: 매일
 *  - WEEKLY: 특정 요일 (routine_days_of_week 사용)
 *  - CUSTOM: N일 간격 (routine_interval_days 사용)
 */
public enum RoutineType {
    ONCE, DAILY, WEEKLY, CUSTOM
}
