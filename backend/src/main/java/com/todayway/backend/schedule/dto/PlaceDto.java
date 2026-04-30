package com.todayway.backend.schedule.dto;

import com.todayway.backend.schedule.domain.PlaceProvider;
import com.todayway.backend.schedule.domain.Schedule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 명세 §11.1 Place 정합. Schedule의 origin/destination 양쪽에서 사용.
 */
public record PlaceDto(
        @NotBlank @Size(max = 100) String name,
        @NotNull BigDecimal lat,
        @NotNull BigDecimal lng,
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
