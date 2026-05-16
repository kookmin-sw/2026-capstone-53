package com.todayway.backend.schedule.dto;

import com.todayway.backend.schedule.domain.PlaceProvider;
import com.todayway.backend.schedule.domain.Schedule;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 명세 §11.1 Place 정합. Schedule의 origin/destination 양쪽에서 사용.
 *
 * <p>{@code lat}/{@code lng} 범위 가드 (v1.1.33) — WGS84 표준 범위 (lat ±90, lng ±180)
 * 밖 값은 {@code 400 VALIDATION_ERROR} 로 fail-fast. 기존엔 {@code @NotNull} 만 두어
 * 클라이언트가 잘못된 좌표를 보내면 ODsay 외부 호출 단계까지 흘러가 5xx 또는 빈
 * 경로 응답이 우리쪽 500 으로 누출되던 결함. v1.1.19 {@code GeocodeRequest} 의
 * NaN/Infinity·XOR 검증과 동일한 진입점 검증 패턴.
 */
public record PlaceDto(
        @NotBlank @Size(max = 100) String name,
        @NotNull
        @DecimalMin(value = "-90.0", message = "lat은 -90 이상이어야 합니다")
        @DecimalMax(value = "90.0", message = "lat은 90 이하여야 합니다")
        BigDecimal lat,
        @NotNull
        @DecimalMin(value = "-180.0", message = "lng은 -180 이상이어야 합니다")
        @DecimalMax(value = "180.0", message = "lng은 180 이하여야 합니다")
        BigDecimal lng,
        @Size(max = 255) String address,
        @Size(max = 100) String placeId,
        PlaceProvider provider
) {
    public static PlaceDto fromOrigin(Schedule s) {
        return new PlaceDto(
                s.getOriginName(), s.getOriginLat(), s.getOriginLng(),
                s.getOriginAddress(), s.getOriginPlaceId(), s.getOriginProvider()
        );
    }

    public static PlaceDto fromDestination(Schedule s) {
        return new PlaceDto(
                s.getDestinationName(), s.getDestinationLat(), s.getDestinationLng(),
                s.getDestinationAddress(), s.getDestinationPlaceId(), s.getDestinationProvider()
        );
    }
}
