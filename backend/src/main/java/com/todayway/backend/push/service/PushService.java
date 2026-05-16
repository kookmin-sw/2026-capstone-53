package com.todayway.backend.push.service;

import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import com.todayway.backend.member.domain.Member;
import com.todayway.backend.member.repository.MemberRepository;
import com.todayway.backend.push.domain.PushSubscription;
import com.todayway.backend.push.dto.PushSubscribeRequest;
import com.todayway.backend.push.dto.PushSubscribeResponse;
import com.todayway.backend.push.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 명세 §7 — Web Push 구독 도메인 서비스. UPSERT (by endpoint) + soft revoke.
 *
 * <p>{@code subscribe()} 의 outer 는 class-default read-only — DB 쓰기는 inner
 * {@link PushSubscriptionUpserter} ({@code REQUIRES_NEW}) 만 수행. inner 의 unique 위반이 outer
 * 의 commit-time {@code UnexpectedRollbackException} 으로 번지지 않는다.
 * {@code unsubscribe()} 는 single row revoke 라 race 위험 X — class-default 를 method-level
 * {@code @Transactional} 로 override.
 *
 * <p>다른 회원이 동일 endpoint 재구독 시도: 명세 §7.1 미정의 영역이라 {@link PushSubscriptionUpserter}
 * 안에서 안전하게 {@code 403 FORBIDDEN_RESOURCE} reject. push provider 가 endpoint 를 device-bound
 * 로 발급하므로 실제 발생 빈도 매우 낮음.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PushService {

    private final MemberRepository memberRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final PushSubscriptionUpserter upserter;

    public PushSubscribeResponse subscribe(String memberUid, PushSubscribeRequest req, String userAgent) {
        Long memberId = resolveMemberId(memberUid);
        PushSubscription saved = upsertWithRetry(memberId, req, userAgent);
        return PushSubscribeResponse.from(saved);
    }

    @Transactional
    public void unsubscribe(String memberUid, String subscriptionUid) {
        Long memberId = resolveMemberId(memberUid);
        PushSubscription s = subscriptionRepository.findBySubscriptionUid(subscriptionUid)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
        if (!s.belongsTo(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_RESOURCE);
        }
        s.revoke();
    }

    /**
     * inner upserter race 충돌은 1회 retry — race window 내 다른 호출이 먼저 INSERT 했다면
     * findByEndpoint 가 즉시 hit. 두 번째 시도도 fail 이면 데이터 불일치 — 500.
     *
     * <p>catch 범위: {@link DuplicateKeyException} (unique 충돌) + {@link TransientDataAccessException}
     * (deadlock / lock wait timeout 등 transient 잠금 실패). 다른 integrity violation 은 그대로 propagate.
     */
    private PushSubscription upsertWithRetry(Long memberId, PushSubscribeRequest req, String userAgent) {
        try {
            return upserter.upsert(memberId, req, userAgent);
        } catch (DuplicateKeyException | TransientDataAccessException e) {
            log.info("Push subscribe UPSERT race detected — single retry, cause={}",
                    e.getClass().getSimpleName());
            try {
                return upserter.upsert(memberId, req, userAgent);
            } catch (DuplicateKeyException | TransientDataAccessException retryEx) {
                // v1.1.35 — 기존엔 INTERNAL_SERVER_ERROR (500) 로 던져 클라이언트가 unrecoverable bug
                // 로 오인하던 결함. 1회 retry 후에도 잡히는 DuplicateKey/Transient 는 본질적으로
                // "일시적 contention, 잠시 후 재시도" 상태라 503 SERVICE_UNAVAILABLE 시맨틱이 맞다.
                // 로그는 ERROR 유지 — 빈번하게 찍히면 동시성 처리 재검토 필요.
                log.error("Push subscribe UPSERT inconsistency after retry — cause={}",
                        retryEx.getClass().getSimpleName(), retryEx);
                throw new BusinessException(ErrorCode.PUSH_SUBSCRIBE_CONFLICT);
            }
        }
    }

    private Long resolveMemberId(String memberUid) {
        return memberRepository.findByMemberUid(memberUid)
                .map(Member::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
