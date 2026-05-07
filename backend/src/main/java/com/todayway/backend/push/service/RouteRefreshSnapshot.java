package com.todayway.backend.push.service;

import java.time.OffsetDateTime;

/**
 * ODsay 재호출 결과 캡처. 트랜잭션 밖 호출이라 entity mutate 가 무의미하므로 결과 값 자체를 record
 * 로 들고 다닌다 — payload 빌드(트랜잭션 밖) + write 트랜잭션의 entity 반영(reload 후 재적용) 모두에서
 * 사용. 명세 §9.1 v1.1.16 트랜잭션 분리 패턴.
 *
 * <p>{@code refreshOk = false} 인 경우 나머지 필드는 모두 null — 폴백 분기에서는 caller 가 detached
 * schedule 의 기존 값을 사용한다.
 */
public record RouteRefreshSnapshot(
        boolean refreshOk,
        Integer estimatedDurationMinutes,
        OffsetDateTime recommendedDepartureTime,
        String routeSummaryJson,
        OffsetDateTime calculatedAt
) {
    public static RouteRefreshSnapshot failed() {
        return new RouteRefreshSnapshot(false, null, null, null, null);
    }
}
