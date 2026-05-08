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
 * <p>{@link GeocodeService#geocode} 가 회원 데이터를 다루지 않아 {@code @CurrentMember} 주입 불필요 —
 * 인증 게이트만 통과하면 cache + 외부 호출만 수행. 인증 미통과 401 회귀 가드는 통합 테스트가 담당.
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
