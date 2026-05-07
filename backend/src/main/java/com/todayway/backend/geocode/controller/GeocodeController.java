package com.todayway.backend.geocode.controller;

import com.todayway.backend.common.response.ApiResponse;
import com.todayway.backend.geocode.dto.GeocodeRequest;
import com.todayway.backend.geocode.dto.GeocodeResponse;
import com.todayway.backend.geocode.service.GeocodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 명세 §8.1 — {@code POST /geocode}.
 * <p>인증은 SecurityConfig {@code anyRequest().authenticated()} 로 강제 — endpoint 가 permitAll 에
 * 등록되지 않았으므로 토큰 없으면 자동 401. {@link GeocodeService#geocode} 자체는 회원 데이터를 다루지
 * 않아 {@code @CurrentMember} 주입은 불필요 (인증 게이트만 통과하면 cache + 외부 호출만 수행).
 */
@RestController
@RequestMapping("/api/v1/geocode")
@RequiredArgsConstructor
public class GeocodeController {

    private final GeocodeService geocodeService;

    @PostMapping
    public ResponseEntity<ApiResponse<GeocodeResponse>> geocode(
            @Valid @RequestBody GeocodeRequest request) {
        return ResponseEntity.ok(ApiResponse.of(geocodeService.geocode(request)));
    }
}
