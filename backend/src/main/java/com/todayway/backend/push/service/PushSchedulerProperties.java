package com.todayway.backend.push.service;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@link PushScheduler} 운영 튜닝값. {@code application.yml} {@code push.scheduler.*}.
 *
 * <p>기본값은 명세 §9.1 정확히 정합 — 운영 환경에서만 환경변수로 override (명세 동작 변경 X).
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "push.scheduler")
public class PushSchedulerProperties {

    /** 스케줄러 자체 on/off. 테스트/dev 환경에서 끄기 위한 토글. */
    private boolean enabled = true;

    /** 명세 §9.1 — fixedDelay 30초 (밀리초). */
    @Min(1000)
    private long fixedDelayMs = 30_000;

    /** 명세 §9.1 — 누락 방지 윈도우 5분. {@code reminder_at > NOW() - INTERVAL window MINUTE}. */
    @Min(1)
    private int windowMinutes = 5;

    /** 명세 §9.1 — ODsay 재호출 총 시도 횟수 (첫 호출 포함). 명세 "최대 2회 시도" → 2. */
    @Min(1)
    private int odsayMaxAttempts = 2;

    /** 명세 §9.1 — 시도 간 sleep 간격 (밀리초). 첫 시도 후부터 적용. */
    @Min(0)
    private long odsayRetryIntervalMs = 1000;
}
