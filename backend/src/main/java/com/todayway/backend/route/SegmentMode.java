package com.todayway.backend.route;

/**
 * 경로 구간 이동 수단. 명세 §6.1 / §11.5 `RouteSegment.mode` 정합.
 * <p>외부 ODsay 응답 의존성 격리 — 도메인엔 우리 enum만 노출.
 */
public enum SegmentMode {
    WALK,
    BUS,
    SUBWAY;

    /**
     * ODsay {@code subPath[].trafficType} → {@link SegmentMode} 매핑.
     * <p>명세 §6.1 v1.1.4 매핑표: {@code 1=SUBWAY, 2=BUS, 3=WALK}.
     *
     * @throws IllegalArgumentException 명세 외 값 (ODsay 스펙 변경 시 OdsayRouteService에서 catch)
     */
    public static SegmentMode fromOdsayTrafficType(int trafficType) {
        return switch (trafficType) {
            case 1 -> SUBWAY;
            case 2 -> BUS;
            case 3 -> WALK;
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 ODsay trafficType: " + trafficType);
        };
    }
}
