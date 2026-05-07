package com.todayway.backend.push.service;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@link PushScheduler} 운영 튜닝값. {@code application.yml} {@code push.scheduler.*}.
 *
 * <p>운영 채널 일원화: {@code enabled} 는 {@link PushSchedulingConfig} 의 {@code @ConditionalOnProperty}
 * 가, {@code fixed-delay-ms} 는 {@link PushScheduler} 의 {@code @Scheduled(fixedDelayString=…)} SpEL
 * 가 직접 읽어 본 클래스에 binding 하지 않는다 — 두 layer 가 같은 값을 토글하는 것처럼 보이는 혼란 차단.
 *
 * <p>본 클래스는 dispatcher 동작 파라미터 (window / ODsay 재시도) 만 담는다.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "push.scheduler")
public class PushSchedulerProperties {

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
