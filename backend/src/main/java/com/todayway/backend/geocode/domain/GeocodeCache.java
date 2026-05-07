package com.todayway.backend.geocode.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 명세 §8.1 — Kakao/NAVER Geocoding 결과 캐시 (TTL 30일). V1__init.sql {@code geocode_cache} 정합.
 *
 * <p>row identity = {@code (query_hash, provider)} UNIQUE. 동일 쿼리·동일 provider 결과는 단일 row 로
 * 보존되며, TTL 만료 후 재호출 시 {@link #refresh} 로 갱신 (UPSERT 시 unique 위반 catch + retry 로
 * race-safe).
 *
 * <p>matched=false (Kakao documents 빈 배열) 도 캐시 — 같은 미스 query 의 반복 호출이 외부 API
 * quota 를 소모하지 않게 한다. 서비스 흐름은 {@link #isMatched} 가 false 면 404 GEOCODE_NO_MATCH.
 *
 * <p>BaseEntity 미상속 ({@code updated_at} 컬럼 없음) — append + refresh 외 상태 없음.
 */
@Getter
@Entity
@Table(name = "geocode_cache")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GeocodeCache {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "query_hash", nullable = false, updatable = false, columnDefinition = "CHAR(64)")
    private String queryHash;

    @Column(name = "query_text", nullable = false, length = 255)
    private String queryText;

    @Column(name = "matched", nullable = false)
    private boolean matched;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "lat", precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(name = "lng", precision = 10, scale = 7)
    private BigDecimal lng;

    @Column(name = "place_id", length = 100)
    private String placeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, updatable = false,
            columnDefinition = "ENUM('NAVER','KAKAO_LOCAL')")
    private GeocodeCacheProvider provider;

    @Column(name = "cached_at", nullable = false)
    private OffsetDateTime cachedAt;

    private GeocodeCache(String queryHash, String queryText, GeocodeCacheProvider provider,
                         boolean matched, String name, String address,
                         BigDecimal lat, BigDecimal lng, String placeId) {
        this.queryHash = queryHash;
        this.queryText = queryText;
        this.provider = provider;
        this.matched = matched;
        this.name = name;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
        this.placeId = placeId;
    }

    /** 매칭된 결과 캐시 신규 생성. {@link MatchedFields} 로 9-positional-arg 의 swap 위험 차단. */
    public static GeocodeCache match(String queryHash, String queryText, GeocodeCacheProvider provider,
                                     MatchedFields fields) {
        return new GeocodeCache(queryHash, queryText, provider, true,
                fields.name(), fields.address(), fields.lat(), fields.lng(), fields.placeId());
    }

    /** Kakao documents 빈 배열 응답을 캐시 (반복 미스 query 의 외부 API 호출 차단). */
    public static GeocodeCache miss(String queryHash, String queryText, GeocodeCacheProvider provider) {
        return new GeocodeCache(queryHash, queryText, provider, false, null, null, null, null, null);
    }

    @PrePersist
    void prePersist() {
        if (cachedAt == null) {
            cachedAt = OffsetDateTime.now(KST);
        }
    }

    /** TTL 만료 후 재조회 결과로 row 갱신. {@code cachedAt} 도 NOW 로 reset. */
    public void refreshAsMatch(String queryText, MatchedFields fields) {
        this.queryText = queryText;
        this.matched = true;
        this.name = fields.name();
        this.address = fields.address();
        this.lat = fields.lat();
        this.lng = fields.lng();
        this.placeId = fields.placeId();
        this.cachedAt = OffsetDateTime.now(KST);
    }

    public void refreshAsMiss(String queryText) {
        this.queryText = queryText;
        this.matched = false;
        this.name = null;
        this.address = null;
        this.lat = null;
        this.lng = null;
        this.placeId = null;
        this.cachedAt = OffsetDateTime.now(KST);
    }
}
