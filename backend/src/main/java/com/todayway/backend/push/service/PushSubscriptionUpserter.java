package com.todayway.backend.push.service;

import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import com.todayway.backend.push.domain.PushSubscription;
import com.todayway.backend.push.dto.PushSubscribeRequest;
import com.todayway.backend.push.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 명세 §7.1 — UPSERT (by endpoint) 의 race-safe 처리. {@link PushService} 의 outer 트랜잭션과 분리된
 * {@code REQUIRES_NEW} 트랜잭션 안에서 INSERT/UPDATE 수행.
 *
 * <p>{@code saveAndFlush} 후 {@link DataIntegrityViolationException} 이 발생하면 Hibernate session 의
 * 트랜잭션이 rollback-only 로 마킹된다. 같은 트랜잭션 안에서 catch + dirty mutate 는 commit 시점에
 * {@link org.springframework.transaction.UnexpectedRollbackException} → silent 500 위험. 별도
 * {@code REQUIRES_NEW} 메서드로 분리해 충돌이 inner tx 안에서 끝나게 한다.
 *
 * <p>다른 회원이 동일 endpoint 재구독 시도는 명세 §7.1 미정의 영역이므로 안전하게 {@code 403
 * FORBIDDEN_RESOURCE} reject — 본 메서드 내부에서 처리.
 *
 * <p>{@code DataIntegrityViolationException} 은 outer 로 propagate — outer 가 1회 retry.
 */
@Service
@RequiredArgsConstructor
public class PushSubscriptionUpserter {

    private final PushSubscriptionRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PushSubscription upsert(Long memberId, PushSubscribeRequest req, String userAgent) {
        Optional<PushSubscription> existing = repository.findByEndpoint(req.endpoint());
        if (existing.isPresent()) {
            return reactivateOwned(existing.get(), memberId, req, userAgent);
        }
        return repository.saveAndFlush(PushSubscription.create(
                memberId, req.endpoint(), req.keys().p256dh(), req.keys().auth(), userAgent));
    }

    private PushSubscription reactivateOwned(PushSubscription existing, Long memberId,
                                             PushSubscribeRequest req, String userAgent) {
        if (!existing.belongsTo(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_RESOURCE);
        }
        existing.reactivate(req.keys().p256dh(), req.keys().auth(), userAgent);
        return existing;
    }
}
