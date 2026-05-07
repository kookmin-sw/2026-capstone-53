package com.todayway.backend.geocode.service;

import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import com.todayway.backend.external.ExternalApiException;
import com.todayway.backend.external.kakao.KakaoLocalClient;
import com.todayway.backend.external.kakao.dto.KakaoLocalSearchResponse;
import com.todayway.backend.geocode.domain.GeocodeCache;
import com.todayway.backend.geocode.domain.GeocodeCacheProvider;
import com.todayway.backend.geocode.domain.MatchedFields;
import com.todayway.backend.geocode.dto.GeocodeRequest;
import com.todayway.backend.geocode.dto.GeocodeResponse;
import com.todayway.backend.geocode.repository.GeocodeCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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
 * <p>핵심 invariant:
 * <ul>
 *   <li>cache TTL filter — 명세 §8.1 30일. miss 캐시도 hit 처리해 외부 API quota 보호.</li>
 *   <li>외부 호출 실패 → 명세 §8.1 매핑표 (401/403=503, 5xx=502, timeout=504).</li>
 *   <li>UPSERT race 는 {@link GeocodeCacheUpserter} 의 {@code REQUIRES_NEW} 트랜잭션이 격리 처리 —
 *       outer 의 read-only tx 가 inner 의 rollback-only 영향을 받지 않게 한다.</li>
 * </ul>
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
    private final GeocodeCacheUpserter cacheUpserter;
    private final KakaoLocalClient kakaoLocalClient;

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
            upsertWithRetry(() -> cacheUpserter.upsertMiss(hash, trimmed, PROVIDER), hash);
            throw new BusinessException(ErrorCode.GEOCODE_NO_MATCH);
        }

        KakaoLocalSearchResponse.Document doc = raw.documents().get(0);
        if (doc.x() == null || doc.y() == null) {
            log.warn("Kakao Local 응답 좌표 누락 query={}, x={}, y={}, id={}",
                    trimmed, doc.x(), doc.y(), doc.id());
            throw new BusinessException(ErrorCode.EXTERNAL_ROUTE_API_FAILED);
        }
        MatchedFields m;
        try {
            m = KakaoLocalToGeocodeMapper.toMatchedFields(doc);
        } catch (NumberFormatException e) {
            // Kakao 가 x/y 를 non-numeric 으로 반환 — 외부 응답 형식 위반 → 명세 §8.1 매핑표 502.
            // raw 값을 함께 로깅해 ops 가 "Kakao 일시 corruption" vs "DTO drift" 구분 가능.
            log.warn("Kakao Local 응답 매핑 실패 — x/y non-numeric query={}, x={}, y={}",
                    trimmed, doc.x(), doc.y(), e);
            throw new BusinessException(ErrorCode.EXTERNAL_ROUTE_API_FAILED);
        }
        GeocodeCache saved = upsertWithRetry(
                () -> cacheUpserter.upsertMatch(hash, trimmed, PROVIDER, m), hash);
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

    /**
     * inner upserter 의 race 충돌은 1회 retry — race window 내 다른 호출이 먼저 INSERT 했다면
     * findByQueryHashAndProvider 가 즉시 hit 한다. 두 번째 시도도 fail 이면 데이터 불일치 — 500.
     */
    private GeocodeCache upsertWithRetry(java.util.function.Supplier<GeocodeCache> action, String hash) {
        try {
            return action.get();
        } catch (DuplicateKeyException e) {
            log.info("Geocode UPSERT race detected — single retry, hash={}, cause={}",
                    hash, e.getMostSpecificCause().getClass().getSimpleName());
            try {
                return action.get();
            } catch (DuplicateKeyException retryEx) {
                log.error("Geocode UPSERT inconsistency after retry — hash={}", hash, retryEx);
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }
    }

    /** 명세 §8.1 v1.1.4 — query_hash = SHA-256(query.trim()). hex 64자 (CHAR(64) 정합). */
    private static String sha256Hex(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
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

    /** 명세 §8.1 매핑표 — ExternalApiException → BusinessException. {@link com.todayway.backend.route.OdsayRouteService} 패턴 미러. */
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
