package com.todayway.backend.geocode.repository;

import com.todayway.backend.geocode.domain.GeocodeCache;
import com.todayway.backend.geocode.domain.GeocodeCacheProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface GeocodeCacheRepository extends JpaRepository<GeocodeCache, Long> {

    /**
     * 명세 §8.1 — TTL filter (cached_at > NOW - 30 days) 적용한 cache hit 조회.
     * UNIQUE (query_hash, provider) 라 단일 row.
     */
    Optional<GeocodeCache> findByQueryHashAndProviderAndCachedAtAfter(
            String queryHash, GeocodeCacheProvider provider, OffsetDateTime cutoff);

    /**
     * UPSERT race / TTL refresh 흐름에서 만료된 row 까지 fetch (cached_at 무관).
     * 도메인 흐름: 1차로 위 TTL 쿼리로 hit 확인, miss 시 외부 호출 후 refresh 가 필요하면 본 메서드 사용.
     */
    Optional<GeocodeCache> findByQueryHashAndProvider(String queryHash, GeocodeCacheProvider provider);

    /**
     * 명세 §8.1 v1.1.18 — TTL eviction. 운영 1년+ 누적 시 row/UNIQUE 인덱스 비대화 차단.
     * {@link com.todayway.backend.geocode.service.GeocodeCacheCleanupScheduler} 가 매일 04:00 KST
     * 호출. read filter 의 30일 TTL 과 같은 cutoff — 만료된 row 만 삭제하고 hit 가능 row 는 보존.
     *
     * @return 삭제된 row 수
     */
    @Modifying
    @Query("DELETE FROM GeocodeCache c WHERE c.cachedAt < :cutoff")
    int deleteByCachedAtBefore(@Param("cutoff") OffsetDateTime cutoff);
}
