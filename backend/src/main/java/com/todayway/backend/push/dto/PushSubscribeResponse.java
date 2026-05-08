package com.todayway.backend.push.dto;

import com.todayway.backend.common.web.IdPrefixes;
import com.todayway.backend.push.domain.PushSubscription;

/**
 * 명세 §7.1 응답 — {@code subscriptionId} 외부 노출 ID 는 명세 §1.7 규약대로
 * {@link IdPrefixes#SUBSCRIPTION} prefix 부착.
 */
public record PushSubscribeResponse(String subscriptionId) {

    public static PushSubscribeResponse from(PushSubscription s) {
        return new PushSubscribeResponse(IdPrefixes.SUBSCRIPTION + s.getSubscriptionUid());
    }
}
