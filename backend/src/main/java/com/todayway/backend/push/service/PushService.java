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
 * 명세 §7 — Web Push 구독 도메인 서비스. UPSERT(by endpoint) + soft revoke.
 *
 * <p>{@link #subscribe} 의 race condition 정책:
 * <ul>
 *   <li>endpoint UNIQUE 제약으로 동시 INSERT 시 두 번째가 {@link DataIntegrityViolationException}.
 *       catch 후 재조회 → reactivate.</li>
 *   <li>{@code saveAndFlush} 로 commit 미루지 않고 즉시 발견 — silent late-commit 충돌 방지.</li>
 * </ul>
 *
 * <p>다른 회원이 동일 endpoint 재구독 시도: 명세 미정의 영역이라 안전하게 {@code 403 FORBIDDEN_RESOURCE}
 * reject. push provider 가 endpoint 를 device-bound 로 발급하므로 실제 발생 빈도 매우 낮으며,
 * "디바이스 소유자 이전" 정책은 P1 보류.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PushService {

    private final MemberRepository memberRepository;
    private final PushSubscriptionRepository subscriptionRepository;

    @Transactional
    public PushSubscribeResponse subscribe(String memberUid, PushSubscribeRequest req, String userAgent) {
        Long memberId = resolveMemberId(memberUid);
        PushSubscription saved = upsertByEndpoint(memberId, req, userAgent);
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

    private PushSubscription upsertByEndpoint(Long memberId, PushSubscribeRequest req, String userAgent) {
        try {
            return subscriptionRepository.findByEndpoint(req.endpoint())
                    .map(existing -> reactivateOwned(existing, memberId, req, userAgent))
                    .orElseGet(() -> subscriptionRepository.saveAndFlush(
                            PushSubscription.create(memberId, req.endpoint(),
                                    req.keys().p256dh(), req.keys().auth(), userAgent)));
        } catch (DataIntegrityViolationException e) {
            // race: 동시 INSERT 충돌 — 두 번째 요청이 unique 위반. 재조회 후 reactivate.
            log.info("Push subscribe UPSERT race — retrying via findByEndpoint");
            PushSubscription existing = subscriptionRepository.findByEndpoint(req.endpoint())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
            return reactivateOwned(existing, memberId, req, userAgent);
        }
    }

    private PushSubscription reactivateOwned(PushSubscription existing, Long memberId,
                                             PushSubscribeRequest req, String userAgent) {
        if (!existing.belongsTo(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_RESOURCE);
        }
        existing.reactivate(req.keys().p256dh(), req.keys().auth(), userAgent);
        return existing;
    }

    private Long resolveMemberId(String memberUid) {
        return memberRepository.findByMemberUid(memberUid)
                .map(Member::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
