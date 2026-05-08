package com.todayway.backend.push.service;

import com.todayway.backend.push.sender.PushSendResult;

/**
 * 한 push subscription 의 발송 결과 (트랜잭션 밖 IO 의 반환값). write 트랜잭션이 reload 후
 * {@link com.todayway.backend.push.domain.PushLog} INSERT + 410 EXPIRED 시 revoke 에 사용한다.
 *
 * <p>{@code subscriptionId} 는 internal DB id — orchestrator 가 detached
 * {@link com.todayway.backend.push.domain.PushSubscription} 에서 캡처. payloadJson 은 sub 별로
 * 다르므로 각 outcome 에 그대로 보관 (명세 §9.1 v1.1.14 sub 별 빌드).
 */
public record SendOutcome(Long subscriptionId, PushSendResult result, String payloadJson) {
}
