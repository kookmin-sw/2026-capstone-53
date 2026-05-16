package com.todayway.backend.push.controller;

import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import com.todayway.backend.common.response.ApiResponse;
import com.todayway.backend.common.web.CurrentMember;
import com.todayway.backend.common.web.IdPrefixes;
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
 * {@link IdPrefixes#SUBSCRIPTION} prefix strip + {@link ApiResponse} 래핑 + 명세 응답 코드.
 */
@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
public class PushController {

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
        // v1.1.35 — strict 검증: `sub_` prefix + ULID26 본문 형식 둘 다 만족해야 통과.
        // 위반 시 400 VALIDATION_ERROR. silent strip 으로 DB lookup 까지 흘려보내면 형식 자체가
        // 잘못된 입력도 404 SUBSCRIPTION_NOT_FOUND 로 응답해 클라이언트 진단 모호 + quota 낭비.
        String subscriptionUid = IdPrefixes.stripAndValidateUlid(subscriptionId, IdPrefixes.SUBSCRIPTION);
        if (subscriptionUid == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        pushService.unsubscribe(memberUid, subscriptionUid);
        return ResponseEntity.noContent().build();
    }
}
