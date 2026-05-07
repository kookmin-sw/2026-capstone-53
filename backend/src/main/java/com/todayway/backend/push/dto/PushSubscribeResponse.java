package com.todayway.backend.push.dto;

import com.todayway.backend.push.domain.PushSubscription;

/**
 * 명세 §7.1 응답 — {@code subscriptionId} 외부 노출 ID는 명세 §1.7 규약대로 {@code sub_} prefix 부착.
 */
public record PushSubscribeResponse(String subscriptionId) {

    private static final String SUBSCRIPTION_ID_PREFIX = "sub_";

    public static PushSubscribeResponse from(PushSubscription s) {
        return new PushSubscribeResponse(SUBSCRIPTION_ID_PREFIX + s.getSubscriptionUid());
    }
}
