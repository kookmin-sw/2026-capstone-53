package com.todayway.backend.geocode.service;

import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import com.todayway.backend.external.ExternalApiException;
import com.todayway.backend.external.kakao.KakaoLocalClient;
import com.todayway.backend.external.kakao.dto.KakaoLocalSearchResponse;
import com.todayway.backend.geocode.domain.GeocodeCache;
import com.todayway.backend.geocode.domain.GeocodeCacheProvider;
import com.todayway.backend.geocode.domain.MatchedFields;
import com.todayway.backend.geocode.dto.GeocodeCandidate;
import com.todayway.backend.geocode.dto.GeocodeRequest;
import com.todayway.backend.geocode.dto.GeocodeResponse;
import com.todayway.backend.geocode.dto.GeocodeSearchRequest;
import com.todayway.backend.geocode.dto.GeocodeSearchResponse;
import com.todayway.backend.geocode.repository.GeocodeCacheRepository;
import com.todayway.backend.schedule.domain.PlaceProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 명세 §8.1 — 주소/장소 지오코딩. Kakao Local 외부 호출 + {@code geocode_cache} TTL 30일.
 *
 * <p>핵심 invariant:
 * <ul>
 *   <li>cache TTL filter — 명세 §8.1 30일. miss 캐시도 hit 처리해 외부 API quota 보호.</li>
 *   <li>외부 호출 실패 → 명세 §8.1 매핑표:
 *       401/403 → 503 EXTERNAL_AUTH_MISCONFIGURED (운영자 alert),
 *       timeout → 504 EXTERNAL_TIMEOUT,
 *       그 외 (4xx-other / 5xx / NETWORK) → 502 EXTERNAL_ROUTE_API_FAILED.</li>
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
        // Kakao 외부 호출에는 사용자 원본 trim 만 한 query 를 보낸다 — 검색 서버가 자체 normalize 보유.
        // hash 는 cache hit ratio 를 위해 추가 정규화 (G1, v1.1.17): squash → NFC → lowercase.
        String canonical = canonicalize(trimmed);
        String hash = sha256Hex(canonical);
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
            // 보안: query 는 사용자 검색어 (PII 위험 — 주소/장소 평문) → hash 로 대체. 같은 query
            // 의 반복 패턴은 hash 매칭으로 추적 가능. doc.id 는 Kakao 내부 식별자라 노출 안전.
            log.warn("Kakao Local 응답 좌표 누락 hash={}, x={}, y={}, kakaoDocId={}",
                    hash, doc.x(), doc.y(), doc.id());
            throw new BusinessException(ErrorCode.EXTERNAL_ROUTE_API_FAILED);
        }
        MatchedFields m;
        try {
            m = KakaoLocalToGeocodeMapper.toMatchedFields(doc);
        } catch (NumberFormatException e) {
            // Kakao 가 x/y 를 non-numeric 으로 반환 — 외부 응답 형식 위반 → 명세 §8.1 매핑표 502.
            // 보안: query 평문 노출 차단 (PII), hash 로 추적 식별. raw x/y 값은 비ASCII 가능성 0이라 안전.
            log.warn("Kakao Local 응답 매핑 실패 — x/y non-numeric hash={}, x={}, y={}",
                    hash, doc.x(), doc.y(), e);
            throw new BusinessException(ErrorCode.EXTERNAL_ROUTE_API_FAILED);
        }
        GeocodeCache saved = upsertWithRetry(
                () -> cacheUpserter.upsertMatch(hash, trimmed, PROVIDER, m), hash);
        return GeocodeResponse.from(saved);
    }

    /**
     * 명세 §8.2 v1.1.27 — 다중 후보 검색. {@link #geocode} 와 달리 cache 미사용 (autocomplete 키스트로크
     * query 는 hit ratio 낮음). Kakao 호출 1회 → 상위 {@code size} 후보 매핑. 좌표 누락/parse 실패
     * row 는 skip — 1건이라도 valid 면 200 반환, 전부 invalid 면 {@link ErrorCode#EXTERNAL_ROUTE_API_FAILED}.
     */
    public GeocodeSearchResponse searchCandidates(GeocodeSearchRequest req) {
        String trimmed = req.query().trim();
        int size = req.sizeOrDefault();

        KakaoLocalSearchResponse raw = callKakao(trimmed);
        if (raw.documents() == null || raw.documents().isEmpty()) {
            throw new BusinessException(ErrorCode.GEOCODE_NO_MATCH);
        }

        int limit = Math.min(size, raw.documents().size());
        List<GeocodeCandidate> candidates = new ArrayList<>(limit);
        int dropped = 0;
        for (int i = 0; i < limit; i++) {
            KakaoLocalSearchResponse.Document doc = raw.documents().get(i);
            if (doc.x() == null || doc.y() == null) {
                // 보안: query PII 차단 — doc.id 만 노출 (Kakao 내부 식별자, 평문 검색어 X).
                log.debug("Kakao Local 응답 후보 좌표 누락 skip kakaoDocId={}, x={}, y={}",
                        doc.id(), doc.x(), doc.y());
                dropped++;
                continue;
            }
            try {
                // 명세 §8.1 v1.1.4 + v1.1.30 — 응답 단계 provider 정규화 (KAKAO_LOCAL → KAKAO).
                candidates.add(GeocodeCandidate.from(doc, PlaceProvider.KAKAO.name()));
            } catch (NumberFormatException e) {
                // 외부 응답 형식 위반. PII 차단 — query 평문/doc 내부 ID 만 hash 없이 출력 (id 는 Kakao 식별자).
                log.warn("Kakao Local 응답 후보 매핑 실패 — x/y non-numeric kakaoDocId={}, x={}, y={}",
                        doc.id(), doc.x(), doc.y());
                dropped++;
            }
        }
        if (candidates.isEmpty()) {
            log.warn("Kakao Local 응답 후보 전체 invalid — documents={}, limit={}, dropped={}",
                    raw.documents().size(), limit, dropped);
            throw new BusinessException(ErrorCode.EXTERNAL_ROUTE_API_FAILED);
        }
        return new GeocodeSearchResponse(candidates);
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
     *
     * <p>catch 범위: {@link DuplicateKeyException} (unique 충돌) + {@link TransientDataAccessException}
     * (deadlock 1213, lock wait timeout 1205 등 transient 잠금 실패). 다른 integrity violation
     * (length truncation, FK, NOT NULL) 은 retry 의미 없어 그대로 propagate.
     */
    private GeocodeCache upsertWithRetry(java.util.function.Supplier<GeocodeCache> action, String hash) {
        try {
            return action.get();
        } catch (DuplicateKeyException | TransientDataAccessException e) {
            log.info("Geocode UPSERT race detected — single retry, hash={}, cause={}",
                    hash, e.getClass().getSimpleName());
            try {
                return action.get();
            } catch (DuplicateKeyException | TransientDataAccessException retryEx) {
                log.error("Geocode UPSERT inconsistency after retry — hash={}", hash, retryEx);
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }
    }

    /**
     * 명세 §8.1 v1.1.17 — cache hit ratio 보강을 위한 query canonicalization.
     * <ol>
     *   <li>trim (caller 측에서 이미 적용됨, 본 메서드에서도 보호적으로 다시 적용)</li>
     *   <li>whitespace squash — 연속 whitespace 를 단일 SPACE 로 (`강남 역` / `강남  역` 같음)</li>
     *   <li>NFC 정규화 — 한글 자모 분리/합치기 입력 동등화 (예: `한국` 의 NFD vs NFC 충돌 차단)</li>
     *   <li>{@link Locale#ROOT} lowercase — 영문 대소문자 동등화 (`GANGNAM` / `gangnam` 같음).
     *       Locale.ROOT 로 Turkish I 문제 회피.</li>
     * </ol>
     * Kakao 호출에는 trim 만 한 사용자 원본을 보내고, 본 canonical 은 cache hash 에만 사용 — 외부
     * 검색 서버가 자체 normalize 를 갖고 있으므로 결과 동등성은 보장.
     */
    private static String canonicalize(String trimmed) {
        String squashed = trimmed.trim().replaceAll("\\s+", " ");
        String nfc = Normalizer.normalize(squashed, Normalizer.Form.NFC);
        return nfc.toLowerCase(Locale.ROOT);
    }

    /** 명세 §8.1 v1.1.17 — query_hash = SHA-256(canonicalize(query)). hex 64자 (CHAR(64) 정합). */
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

    /** 명세 §8.1 매핑표 — ExternalApiException → BusinessException. */
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
