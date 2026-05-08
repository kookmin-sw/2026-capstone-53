package com.todayway.backend.geocode.service;

import com.todayway.backend.geocode.domain.GeocodeCache;
import com.todayway.backend.geocode.domain.GeocodeCacheProvider;
import com.todayway.backend.geocode.repository.GeocodeCacheRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 명세 §8.1 v1.1.18 — {@link GeocodeCacheCleanupScheduler} TTL eviction 동작 가드.
 *
 * <p>검증 매트릭스:
 * <ul>
 *   <li>30일 이상 된 row → 삭제</li>
 *   <li>30일 안의 row → 보존 (read filter 와 같은 cutoff)</li>
 *   <li>cleanup() 직접 호출 — {@code @Scheduled} 트리거 우회 (push.scheduler.enabled=false 라 자동
 *       트리거 X 인 환경에서도 동작 검증).</li>
 * </ul>
 */
@SpringBootTest
@Testcontainers
class GeocodeCacheCleanupSchedulerTest {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final GeocodeCacheProvider PROVIDER = GeocodeCacheProvider.KAKAO_LOCAL;

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("routine_commute");

    @DynamicPropertySource
    static void mysqlProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQtYmFzZTY0LXBhZGRlZC0zMmJ5dGVzLWxvbmc9PQ==");
        registry.add("push.scheduler.enabled", () -> "false");
    }

    @Autowired GeocodeCacheCleanupScheduler scheduler;
    @Autowired GeocodeCacheRepository repository;

    @PersistenceContext
    EntityManager em;

    @Test
    @Transactional
    void cleanup_30일_이상_row만_삭제하고_TTL_안의_row는_보존한다() {
        // arrange — match/miss 두 종류 + 만료/유효 두 시점 = 4 row
        String hashOldMatch = "a".repeat(64);
        String hashOldMiss = "b".repeat(64);
        String hashFreshMatch = "c".repeat(64);
        String hashFreshMiss = "d".repeat(64);

        repository.saveAndFlush(GeocodeCache.miss(hashOldMatch, "old m", PROVIDER));
        repository.saveAndFlush(GeocodeCache.miss(hashOldMiss, "old s", PROVIDER));
        repository.saveAndFlush(GeocodeCache.miss(hashFreshMatch, "fresh m", PROVIDER));
        repository.saveAndFlush(GeocodeCache.miss(hashFreshMiss, "fresh s", PROVIDER));

        // PrePersist 가 cached_at = NOW 로 set 한 후 native UPDATE 로 임의 시점 박음
        OffsetDateTime expired = OffsetDateTime.now(KST).minusDays(31);
        OffsetDateTime fresh = OffsetDateTime.now(KST).minusDays(29);

        em.createNativeQuery("UPDATE geocode_cache SET cached_at = ? WHERE query_hash IN (?, ?)")
                .setParameter(1, expired)
                .setParameter(2, hashOldMatch)
                .setParameter(3, hashOldMiss)
                .executeUpdate();
        em.createNativeQuery("UPDATE geocode_cache SET cached_at = ? WHERE query_hash IN (?, ?)")
                .setParameter(1, fresh)
                .setParameter(2, hashFreshMatch)
                .setParameter(3, hashFreshMiss)
                .executeUpdate();
        em.flush();
        em.clear();

        // act — scheduler 직접 호출 (cron 트리거 우회)
        scheduler.cleanup();
        em.clear();

        // assert
        assertThat(repository.findByQueryHashAndProvider(hashOldMatch, PROVIDER))
                .as("31일 전 match row 는 삭제").isEmpty();
        assertThat(repository.findByQueryHashAndProvider(hashOldMiss, PROVIDER))
                .as("31일 전 miss row 도 삭제 (matched 무관)").isEmpty();
        assertThat(repository.findByQueryHashAndProvider(hashFreshMatch, PROVIDER))
                .as("29일 전 row 는 보존 (TTL 30일 안)").isPresent();
        assertThat(repository.findByQueryHashAndProvider(hashFreshMiss, PROVIDER))
                .as("29일 전 miss row 도 보존").isPresent();
    }

    @Test
    @Transactional
    void cleanup_삭제_대상_없으면_no_op() {
        // arrange — 모두 fresh row 만
        String hash = "e".repeat(64);
        repository.saveAndFlush(GeocodeCache.miss(hash, "fresh only", PROVIDER));
        // cached_at 조정 안 함 — PrePersist 의 NOW 그대로

        // act
        scheduler.cleanup();
        em.clear();

        // assert — 보존
        assertThat(repository.findByQueryHashAndProvider(hash, PROVIDER)).isPresent();
    }
}
