package com.todayway.backend.map.controller;

import com.todayway.backend.common.response.ApiResponse;
import com.todayway.backend.common.web.CurrentMember;
import com.todayway.backend.map.dto.MainResponse;
import com.todayway.backend.map.dto.MapConfigResponse;
import com.todayway.backend.map.service.MainService;
import com.todayway.backend.map.service.MapConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 명세 §4 — 메인/지도 (display, settings).
 * <p>{@code GET /main} 게스트 허용 + {@code GET /map/config} 인증 불필요.
 * SecurityConfig.permitAll 에 두 경로 모두 사전 등록되어 있다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MapController {

    private final MainService mainService;
    private final MapConfigService mapConfigService;

    @GetMapping("/main")
    public ResponseEntity<ApiResponse<MainResponse>> getMain(
            @CurrentMember(required = false) String memberUid,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return ResponseEntity.ok(ApiResponse.of(mainService.compose(memberUid, lat, lng)));
    }

    @GetMapping("/map/config")
    public ResponseEntity<ApiResponse<MapConfigResponse>> getMapConfig() {
        return ResponseEntity.ok(ApiResponse.of(mapConfigService.getConfig()));
    }
}
