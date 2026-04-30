package com.todayway.backend.schedule.domain;

/**
 * 출발시각 조정 안내. 명세 §5.1 ±3분 윈도우 기준.
 *  - EARLIER: recommendedDepartureTime < userDepartureTime - 3min
 *  - ON_TIME: |diff| <= 3min
 *  - LATER:   recommendedDepartureTime > userDepartureTime + 3min
 */
public enum DepartureAdvice {
    EARLIER, ON_TIME, LATER
}
