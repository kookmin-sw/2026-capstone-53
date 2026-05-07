package com.todayway.backend.geocode.service;

import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import com.todayway.backend.external.ExternalApiException;
import com.todayway.backend.external.kakao.KakaoLocalClient;
import com.todayway.backend.external.kakao.dto.KakaoLocalSearchResponse;
import com.todayway.backend.geocode.domain.GeocodeCache;
import com.todayway.backend.geocode.domain.GeocodeCacheProvider;
import com.todayway.backend.geocode.dto.GeocodeRequest;
import com.todayway.backend.geocode.dto.GeocodeResponse;
import com.todayway.backend.geocode.repository.GeocodeCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 명세 §8.1 — 주소/장소 지오코딩. Kakao Local 외부 호출 + {@code geocode_cache} TTL 30일.
 *
 * <p>흐름:
 * <ol>
 *   <li>{@code queryHash = SHA-256(query.trim())} — 명세 §8.1 v1.1.4 정규화 룰.</li>
 *   <li>TTL filter cache lookup (cached_at &gt; NOW - 30일). hit 면 즉시 응답.</li>
 *   <li>matched=false 캐시 hit → 404 GEOCODE_NO_MATCH (외부 API 호출 차단).</li>
 *   <li>miss → Kakao Local 호출. {@link ExternalApiException} 은 명세 §8.1 매핑표대로
 *       {@link BusinessException} 으로 변환 (401/403 → 503, timeout → 504, 그 외 → 502).</li>
 *   <li>documents 빈 배열 → miss row UPSERT + 404.</li>
 *   <li>매치 → match row UPSERT + 응답.</li>
 * </ol>
 *
 * <p>UPSERT race-safe: existing row 있으면 refresh, 없으면 save. {@link DataIntegrityViolationException}
 * (unique (query_hash, provider) 동시 INSERT 충돌) 은 catch 후 재조회 + refresh.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class GeocodeService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final GeocodeCacheProvider PROVIDER = GeocodeCacheProvider.KAKAO_LOCAL;
    private static final int CACHE_TTL_DAYS = 30;

    private final GeocodeCacheRepository cacheRepository;
    private final KakaoLocalClient kakaoLocalClient;

    @Transactional
    public GeocodeResponse geocode(GeocodeRequest req) {
        String trimmed = req.query().trim();
        String hash = sha256Hex(trimmed);
        OffsetDateTime cutoff = OffsetDateTime.now(KST).minusDays(CACHE_TTL_DAYS);

        Optional<GeocodeCache> fresh = cacheRepository
                .findByQueryHashAndProviderAndCachedAtAfter(hash, PROVIDER, cutoff);
        if (fresh.isPresent()) {
            GeocodeCache c = fresh.get();
            if (!c.isMatched()) {
                throw new BusinessException(ErrorCode.GEOCODE_NO_MATCH);
            }
            return GeocodeResponse.from(c);
        }

        KakaoLocalSearchResponse raw = callKakao(trimmed);

        if (raw.documents() == null || raw.documents().isEmpty()) {
            upsertMiss(hash, trimmed);
            throw new BusinessException(ErrorCode.GEOCODE_NO_MATCH);
        }

        KakaoLocalToGeocodeMapper.MatchedFields m =
                KakaoLocalToGeocodeMapper.toMatchedFields(raw.documents().get(0));
        GeocodeCache saved = upsertMatch(hash, trimmed, m);
        return GeocodeResponse.from(saved);
    }

    private KakaoLocalSearchResponse callKakao(String query) {
        try {
            return kakaoLocalClient.searchKeyword(query);
        } catch (ExternalApiException e) {
            if (isAuthError(e)) {
                log.error("Kakao Local 401/403 (auth 미설정/만료, 운영자 alert) httpStatus={}",
                        e.getHttpStatus(), e);
            } else {
                log.warn("Kakao Local 호출 실패 type={} httpStatus={}",
                        e.getType(), e.getHttpStatus(), e);
            }
            throw mapToBusinessException(e);
        }
    }

    private GeocodeCache upsertMatch(String hash, String queryText, KakaoLocalToGeocodeMapper.MatchedFields m) {
        Optional<GeocodeCache> existing = cacheRepository.findByQueryHashAndProvider(hash, PROVIDER);
        if (existing.isPresent()) {
            existing.get().refreshAsMatch(queryText, m.name(), m.address(), m.lat(), m.lng(), m.placeId());
            return existing.get();
        }
        try {
            return cacheRepository.saveAndFlush(
                    GeocodeCache.match(hash, queryText, PROVIDER,
                            m.name(), m.address(), m.lat(), m.lng(), m.placeId()));
        } catch (DataIntegrityViolationException e) {
            // race: 동시 INSERT 충돌 — 재조회 후 refresh.
            log.info("Geocode UPSERT match race — retrying via findByQueryHashAndProvider, cause={}",
                    e.getMostSpecificCause().getClass().getSimpleName());
            GeocodeCache row = cacheRepository.findByQueryHashAndProvider(hash, PROVIDER)
                    .orElseThrow(() -> {
                        log.error("Geocode UPSERT inconsistency — DataIntegrityViolation but row not found", e);
                        return new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
                    });
            row.refreshAsMatch(queryText, m.name(), m.address(), m.lat(), m.lng(), m.placeId());
            return row;
        }
    }

    private void upsertMiss(String hash, String queryText) {
        Optional<GeocodeCache> existing = cacheRepository.findByQueryHashAndProvider(hash, PROVIDER);
        if (existing.isPresent()) {
            existing.get().refreshAsMiss(queryText);
            return;
        }
        try {
            cacheRepository.saveAndFlush(GeocodeCache.miss(hash, queryText, PROVIDER));
        } catch (DataIntegrityViolationException e) {
            log.info("Geocode UPSERT miss race — retrying via findByQueryHashAndProvider, cause={}",
                    e.getMostSpecificCause().getClass().getSimpleName());
            cacheRepository.findByQueryHashAndProvider(hash, PROVIDER)
                    .ifPresent(c -> c.refreshAsMiss(queryText));
        }
    }

    /** 명세 §8.1 v1.1.4 — query_hash = SHA-256(query.trim()). hex 64자 (CHAR(64) 정합). */
    private static String sha256Hex(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 JDK 표준 — 발생 불가. 발생 시 fail-fast.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** 401/403 — Kakao API 키 미설정/만료. 운영자 alert. */
    private static boolean isAuthError(ExternalApiException e) {
        if (e.getType() != ExternalApiException.Type.CLIENT_ERROR) {
            return false;
        }
        Integer status = e.getHttpStatus();
        return status != null && (status == 401 || status == 403);
    }

    /** 명세 §8.1 매핑표 — ExternalApiException → BusinessException. OdsayRouteService 패턴 미러. */
    private static BusinessException mapToBusinessException(ExternalApiException e) {
        if (isAuthError(e)) {
            return new BusinessException(ErrorCode.EXTERNAL_AUTH_MISCONFIGURED);
        }
        return switch (e.getType()) {
            case TIMEOUT -> new BusinessException(ErrorCode.EXTERNAL_TIMEOUT);
            case CLIENT_ERROR, SERVER_ERROR, NETWORK ->
                    new BusinessException(ErrorCode.EXTERNAL_ROUTE_API_FAILED);
        };
    }
}
