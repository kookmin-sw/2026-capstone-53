package com.todayway.backend.geocode.service;

import com.todayway.backend.geocode.repository.GeocodeCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 명세 §8.1 v1.1.18 — geocode_cache TTL eviction. 운영 1년+ 누적 시 row 와 UNIQUE INDEX
 * (query_hash, provider) 비대화 차단.
 *
 * <p>read filter 의 30일 TTL 과 같은 cutoff — 만료된 row 만 삭제하고 hit 가능 row 는 보존.
 * 매일 04:00 KST (운영 부하 적은 시간) 트리거. cron / TTL / enabled 모두 환경변수 외부화.
 *
 * <p>{@code @ConditionalOnProperty(matchIfMissing=true)} — 통합 테스트가
 * {@link com.todayway.backend.push.service.PushSchedulingConfig} 의 push.scheduler.enabled=false
 * 로 {@code @EnableScheduling} 자체를 끄므로 본 빈은 등록되어도 {@code @Scheduled} 트리거 X.
 * 별도로 {@code geocode.cleanup.enabled=false} 로 빈 등록 자체를 막을 수도 있음.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "geocode.cleanup", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class GeocodeCacheCleanupScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int CACHE_TTL_DAYS = 30;

    private final GeocodeCacheRepository cacheRepository;

    @Scheduled(cron = "${geocode.cleanup.cron:0 0 4 * * *}", zone = "Asia/Seoul")
    @Transactional
    public void cleanup() {
        OffsetDateTime cutoff = OffsetDateTime.now(KST).minusDays(CACHE_TTL_DAYS);
        int deleted = cacheRepository.deleteByCachedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Geocode cache TTL eviction — deleted {} rows older than {} days (cutoff={})",
                    deleted, CACHE_TTL_DAYS, cutoff);
        } else {
            log.debug("Geocode cache TTL eviction — no expired rows (cutoff={})", cutoff);
        }
    }
}
