package com.todayway.backend.geocode.repository;

import com.todayway.backend.geocode.domain.GeocodeCache;
import com.todayway.backend.geocode.domain.GeocodeCacheProvider;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
