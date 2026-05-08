package com.todayway.backend.map.service;

import com.todayway.backend.map.config.MapConfigProperties;
import com.todayway.backend.map.dto.MapConfigResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 명세 §4.2 — 지도 SDK 설정 정적 응답. DB 미사용.
 *
 * <p>설정값 자체가 부적합하면 {@link MapConfigProperties} 의 {@code @Validated} 가 ApplicationContext
 * 시작 시점에 fail 시키므로 본 service 는 단순 변환만 수행. 명세 §4.2 의 503 MAP_PROVIDER_UNAVAILABLE
 * ErrorCode 는 본 흐름에선 throw 위치 0건 (current dead code) — 외부 SDK 헬스체크 (예: Naver Maps
 * SDK availability ping) 가 본 service 또는 별도 health endpoint 로 추가될 때 발화 경로가 생긴다.
 * 그 시점에 헬스체크 결과 → {@code BusinessException(ErrorCode.MAP_PROVIDER_UNAVAILABLE)} 매핑.
 */
@Service
@RequiredArgsConstructor
public class MapConfigService {

    private final MapConfigProperties properties;

    public MapConfigResponse getConfig() {
        return MapConfigResponse.from(properties);
    }
}
