package com.todayway.backend.map.dto;

import com.todayway.backend.map.config.MapConfigProperties;

/**
 * 명세 §4.2 — {@code GET /map/config} 응답. 클라이언트 API 키는 포함하지 않는다 (프론트 빌드시 주입).
 */
public record MapConfigResponse(
        String provider,
        int defaultZoom,
        Coordinate defaultCenter,
        String tileStyle
) {

    public static MapConfigResponse from(MapConfigProperties p) {
        return new MapConfigResponse(
                p.provider(),
                p.defaultZoom(),
                new Coordinate(p.defaultCenter().lat(), p.defaultCenter().lng()),
                p.tileStyle()
        );
    }
}
