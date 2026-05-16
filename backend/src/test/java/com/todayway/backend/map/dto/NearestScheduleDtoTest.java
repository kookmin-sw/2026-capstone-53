package com.todayway.backend.map.dto;

import com.todayway.backend.schedule.domain.PlaceProvider;
import com.todayway.backend.schedule.domain.RoutineType;
import com.todayway.backend.schedule.domain.Schedule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 명세 §4.1 {@code nearestSchedule} graceful 직렬화 회귀 가드. v1.1.34 —
 * corrupted coordinate (V1 NOT NULL 우회 / 직접 SQL) 가 흘러들어와도 {@code /main} 이
 * 500 으로 죽지 않도록 mapper 가 null 반환 + WARN 격하해야 한다는 invariant 검증.
 *
 * <p>JPA {@code @Column(nullable=false)} 가 boundary 가드지만, factory {@link Schedule#create}
 * 는 자체 검증을 하지 않으므로 in-memory 로 corrupted Schedule 을 만들어 mapper 만 검증.
 */
class NearestScheduleDtoTest {

    private static final OffsetDateTime ARRIVAL = OffsetDateTime.of(2026, 5, 16, 9, 0, 0, 0, ZoneOffset.ofHours(9));

    @Test
    void 정상_좌표_DTO_정상_변환() {
        Schedule s = build(bd("37.5"), bd("127.0"), bd("37.6"), bd("127.1"));
        NearestScheduleDto dto = NearestScheduleDto.from(s);
        assertThat(dto).isNotNull();
        assertThat(dto.origin().lat()).isEqualTo(37.5);
        assertThat(dto.destination().lng()).isEqualTo(127.1);
    }

    @Test
    void origin_lat_null_시_null_반환_500_방지() {
        Schedule s = build(null, bd("127.0"), bd("37.6"), bd("127.1"));
        assertThat(NearestScheduleDto.from(s)).isNull();
    }

    @Test
    void origin_lng_null_시_null_반환_500_방지() {
        Schedule s = build(bd("37.5"), null, bd("37.6"), bd("127.1"));
        assertThat(NearestScheduleDto.from(s)).isNull();
    }

    @Test
    void destination_lat_null_시_null_반환_500_방지() {
        Schedule s = build(bd("37.5"), bd("127.0"), null, bd("127.1"));
        assertThat(NearestScheduleDto.from(s)).isNull();
    }

    @Test
    void destination_lng_null_시_null_반환_500_방지() {
        Schedule s = build(bd("37.5"), bd("127.0"), bd("37.6"), null);
        assertThat(NearestScheduleDto.from(s)).isNull();
    }

    private static Schedule build(BigDecimal oLat, BigDecimal oLng, BigDecimal dLat, BigDecimal dLng) {
        return Schedule.create(1L, "회귀가드",
                "우이동", oLat, oLng, null, null, PlaceProvider.MANUAL,
                "국민대", dLat, dLng, null, null, PlaceProvider.MANUAL,
                ARRIVAL.minusMinutes(30), ARRIVAL, 5,
                RoutineType.ONCE, null, null,
                null, null);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
