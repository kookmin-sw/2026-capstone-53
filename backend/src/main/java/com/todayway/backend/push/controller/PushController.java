package com.todayway.backend.push.controller;

import com.todayway.backend.common.response.ApiResponse;
import com.todayway.backend.common.web.CurrentMember;
import com.todayway.backend.push.dto.PushSubscribeRequest;
import com.todayway.backend.push.dto.PushSubscribeResponse;
import com.todayway.backend.push.service.PushService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 명세 §7.1 / §7.2 — Web Push 구독 등록/해제.
 * <p>인증/인가 검증은 {@link PushService} 가 담당. 본 controller 는 HTTP 레이어 변환만 —
 * {@code sub_} prefix strip + {@link ApiResponse} 래핑 + 명세 응답 코드 (201 Created / 204 No Content).
 */
@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
public class PushController {

    private static final String SUBSCRIPTION_ID_PREFIX = "sub_";

    private final PushService pushService;

    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse<PushSubscribeResponse>> subscribe(
            @CurrentMember String memberUid,
            @Valid @RequestBody PushSubscribeRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        PushSubscribeResponse resp = pushService.subscribe(memberUid, request, userAgent);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(resp));
    }

    @DeleteMapping("/subscribe/{subscriptionId}")
    public ResponseEntity<Void> unsubscribe(
            @CurrentMember String memberUid,
            @PathVariable String subscriptionId) {
        pushService.unsubscribe(memberUid, stripPrefix(subscriptionId));
        return ResponseEntity.noContent().build();
    }

    /** 명세 §1.7 — 외부 노출 ID는 {@code sub_} prefix. 내부 ULID로 변환. */
    private static String stripPrefix(String subscriptionId) {
        if (subscriptionId != null && subscriptionId.startsWith(SUBSCRIPTION_ID_PREFIX)) {
            return subscriptionId.substring(SUBSCRIPTION_ID_PREFIX.length());
        }
        return subscriptionId;
    }
}
