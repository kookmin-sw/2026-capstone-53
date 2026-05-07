package com.todayway.backend.map.controller;

import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import com.todayway.backend.common.response.ApiResponse;
import com.todayway.backend.common.web.CurrentMember;
import com.todayway.backend.map.dto.MainResponse;
import com.todayway.backend.map.dto.MapConfigResponse;
import com.todayway.backend.map.service.MainService;
import com.todayway.backend.map.service.MapConfigService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 명세 §4 — 메인/지도 (display, settings).
 * <p>{@code GET /main} 은 명세 §4.1 게스트 허용 ({@code @CurrentMember(required=false)}),
 * {@code GET /map/config} 는 인증 불필요. 두 경로 모두 통합 테스트가 401 회귀 가드 담당.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class MapController {

    private final MainService mainService;
    private final MapConfigService mapConfigService;

    @GetMapping("/main")
    public ResponseEntity<ApiResponse<MainResponse>> getMain(
            @CurrentMember(required = false) String memberUid,
            @RequestParam(required = false) @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @RequestParam(required = false) @DecimalMin("-180.0") @DecimalMax("180.0") Double lng) {
        // @DecimalMin/@DecimalMax 가 NaN/Infinity 를 그대로 통과시키는 케이스가 있어 (BigDecimal
        // 비교 시 false → @DecimalMax/Min 통과) 명시적 가드. 사용자 입력 오류는 400 — 통과 시
        // Coordinate compact ctor 가 IAE 를 던져 catch-all 500 으로 떨어지는 silent regression 차단.
        if ((lat != null && !Double.isFinite(lat)) || (lng != null && !Double.isFinite(lng))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return ResponseEntity.ok(ApiResponse.of(mainService.compose(memberUid, lat, lng)));
    }

    @GetMapping("/map/config")
    public ResponseEntity<ApiResponse<MapConfigResponse>> getMapConfig() {
        return ResponseEntity.ok(ApiResponse.of(mapConfigService.getConfig()));
    }
}
