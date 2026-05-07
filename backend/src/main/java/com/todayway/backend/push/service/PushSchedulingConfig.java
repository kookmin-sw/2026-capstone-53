package com.todayway.backend.push.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 명세 §9.1 — {@link PushScheduler} 활성화. {@code @ConditionalOnProperty} 로 통합 테스트/dev 환경에서
 * {@code push.scheduler.enabled=false} 설정 시 {@code @EnableScheduling} 자체가 미적용 →
 * {@code @Scheduled} 빈도 등록 X.
 *
 * <p>기본값 {@code true} (matchIfMissing) — 운영 환경은 별도 설정 없이 동작.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "push.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PushSchedulingConfig {
}
