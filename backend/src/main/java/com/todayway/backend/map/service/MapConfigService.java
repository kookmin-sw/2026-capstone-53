package com.todayway.backend.map.service;

import com.todayway.backend.map.config.MapConfigProperties;
import com.todayway.backend.map.dto.MapConfigResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 명세 §4.2 — 지도 SDK 설정 정적 응답. DB 미사용.
 *
 * <p>설정값 자체가 부적합하면 {@link MapConfigProperties} 의 {@code @Validated} 가 ApplicationContext
 * 시작 시점에 fail 시키므로 본 service 는 단순 변환만 수행. 명세에 박힌 503 MAP_PROVIDER_UNAVAILABLE 은
 * 정적 설정 흐름에서는 도달 불가 (P1 — 외부 SDK 헬스체크 도입 시 발화 예정).
 */
@Service
@RequiredArgsConstructor
public class MapConfigService {

    private final MapConfigProperties properties;

    public MapConfigResponse getConfig() {
        return MapConfigResponse.from(properties);
    }
}
