package com.todayway.backend.geocode.service;

import com.todayway.backend.geocode.domain.GeocodeCache;
import com.todayway.backend.geocode.domain.GeocodeCacheProvider;
import com.todayway.backend.geocode.domain.MatchedFields;
import com.todayway.backend.geocode.repository.GeocodeCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 명세 §8.1 cache UPSERT 의 race-safe 처리. 외부({@link GeocodeService})의 transaction 과 분리된
 * {@code REQUIRES_NEW} 트랜잭션 안에서 INSERT/UPDATE 를 수행한다.
 *
 * <p>왜 별도 service?
 * {@code saveAndFlush} 후 {@link DataIntegrityViolationException} 이 발생하면 Hibernate 는 현재
 * session 의 트랜잭션을 rollback-only 로 표시한다. 같은 트랜잭션 안에서 catch 후 entity 를 dirty-
 * mutate 하면 commit 시점에 {@link org.springframework.transaction.UnexpectedRollbackException}
 * 으로 떨어져 race window 가 silent 500 으로 변한다. 별도 {@code REQUIRES_NEW} 메서드로 분리하면
 * 충돌이 inner tx 안에서 끝나고 outer tx 는 영향받지 않는다.
 *
 * <p>{@code DataIntegrityViolationException} 은 그대로 outer 로 propagate — outer 가 1회 retry.
 */
@Service
@RequiredArgsConstructor
public class GeocodeCacheUpserter {

    private final GeocodeCacheRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GeocodeCache upsertMatch(String queryHash, String queryText, GeocodeCacheProvider provider,
                                    MatchedFields fields) {
        return upsert(queryHash, provider,
                existing -> existing.refreshAsMatch(queryText, fields),
                () -> GeocodeCache.match(queryHash, queryText, provider, fields));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GeocodeCache upsertMiss(String queryHash, String queryText, GeocodeCacheProvider provider) {
        return upsert(queryHash, provider,
                existing -> existing.refreshAsMiss(queryText),
                () -> GeocodeCache.miss(queryHash, queryText, provider));
    }

    /**
     * find-or-create 공통 분기. {@code refresh} / {@code create} 두 람다로 match/miss 의 차이만
     * 표현 — 둘 다 본 트랜잭션 안에서 호출되므로 inner tx 격리 invariant 동일.
     */
    private GeocodeCache upsert(String queryHash, GeocodeCacheProvider provider,
                                Consumer<GeocodeCache> refresh, Supplier<GeocodeCache> create) {
        Optional<GeocodeCache> existing = repository.findByQueryHashAndProvider(queryHash, provider);
        if (existing.isPresent()) {
            refresh.accept(existing.get());
            return existing.get();
        }
        return repository.saveAndFlush(create.get());
    }
}
