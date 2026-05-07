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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 명세 §7 — Web Push 구독 도메인 서비스. UPSERT (by endpoint) + soft revoke.
 *
 * <p>UPSERT 의 race 처리는 {@link PushSubscriptionUpserter} 의 {@code REQUIRES_NEW} 트랜잭션이
 * 격리한다. 본 서비스의 outer transaction 은 read-only — inner 의 rollback-only 가 outer commit 에
 * 영향을 주지 않게 해 race 가 silent 500 으로 떨어지지 않게 한다.
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
     */
    private PushSubscription upsertWithRetry(Long memberId, PushSubscribeRequest req, String userAgent) {
        try {
            return upserter.upsert(memberId, req, userAgent);
        } catch (DataIntegrityViolationException e) {
            log.info("Push subscribe UPSERT race detected — single retry, cause={}",
                    e.getMostSpecificCause().getClass().getSimpleName());
            try {
                return upserter.upsert(memberId, req, userAgent);
            } catch (DataIntegrityViolationException retryEx) {
                log.error("Push subscribe UPSERT inconsistency after retry", retryEx);
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }
    }

    private Long resolveMemberId(String memberUid) {
        return memberRepository.findByMemberUid(memberUid)
                .map(Member::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
