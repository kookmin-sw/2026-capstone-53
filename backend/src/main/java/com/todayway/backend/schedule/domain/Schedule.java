package com.todayway.backend.schedule.domain;

import com.todayway.backend.common.entity.BaseEntity;
import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import com.todayway.backend.common.ulid.UlidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 일정 도메인. 출/도착지 + 시간 + ODsay 응답(raw JSON) + 루틴 + 알림 시각.
 * 명세 §5 / §11.3 / V1__init.sql `schedule` 테이블 정합.
 *
 * 주요 책임:
 *  - 비즈니스 invariant 가드 (deleted 후 변경 차단)
 *  - 부분 업데이트 변경 영향도 도출 (ODsay 재호출 분기)
 *  - reminderAt 자동 재계산
 */
@Getter
@Entity
@Table(name = "schedule")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule extends BaseEntity {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long ON_TIME_WINDOW_MINUTES = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_uid", nullable = false, updatable = false, unique = true,
            columnDefinition = "CHAR(26)")
    private String scheduleUid;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(nullable = false, length = 100)
    private String title;

    // ─── 출발지 ───
    @Column(name = "origin_name", nullable = false, length = 100)
    private String originName;

    @Column(name = "origin_lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal originLat;

    @Column(name = "origin_lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal originLng;

    @Column(name = "origin_address", length = 255)
    private String originAddress;

    @Column(name = "origin_place_id", length = 100)
    private String originPlaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_provider", columnDefinition = "ENUM('NAVER','KAKAO','ODSAY','MANUAL')")
    private PlaceProvider originProvider;

    // ─── 도착지 ───
    @Column(name = "destination_name", nullable = false, length = 100)
    private String destinationName;

    @Column(name = "destination_lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal destinationLat;

    @Column(name = "destination_lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal destinationLng;

    @Column(name = "destination_address", length = 255)
    private String destinationAddress;

    @Column(name = "destination_place_id", length = 100)
    private String destinationPlaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_provider", columnDefinition = "ENUM('NAVER','KAKAO','ODSAY','MANUAL')")
    private PlaceProvider destinationProvider;

    // ─── 시간 ───
    @Column(name = "user_departure_time", nullable = false)
    private OffsetDateTime userDepartureTime;

    @Column(name = "arrival_time", nullable = false)
    private OffsetDateTime arrivalTime;

    // ─── ODsay 결과 ───
    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column(name = "recommended_departure_time")
    private OffsetDateTime recommendedDepartureTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "departure_advice", columnDefinition = "ENUM('EARLIER','ON_TIME','LATER')")
    private DepartureAdvice departureAdvice;

    @Column(name = "route_summary_json", columnDefinition = "JSON")
    private String routeSummaryJson;

    @Column(name = "route_calculated_at")
    private OffsetDateTime routeCalculatedAt;

    // ─── 알림 ───
    @Column(name = "reminder_offset_minutes", nullable = false)
    private Integer reminderOffsetMinutes;

    @Column(name = "reminder_at")
    private OffsetDateTime reminderAt;

    // ─── 루틴 ───
    @Enumerated(EnumType.STRING)
    @Column(name = "routine_type", columnDefinition = "ENUM('ONCE','DAILY','WEEKLY','CUSTOM')")
    private RoutineType routineType;

    @Column(name = "routine_days_of_week", length = 20)
    private String routineDaysOfWeek;

    @Column(name = "routine_interval_days")
    private Integer routineIntervalDays;

    // ─── 소프트 삭제 ───
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    private Schedule(Long memberId, String title,
                     String originName, BigDecimal originLat, BigDecimal originLng,
                     String originAddress, String originPlaceId, PlaceProvider originProvider,
                     String destinationName, BigDecimal destinationLat, BigDecimal destinationLng,
                     String destinationAddress, String destinationPlaceId, PlaceProvider destinationProvider,
                     OffsetDateTime userDepartureTime, OffsetDateTime arrivalTime,
                     Integer reminderOffsetMinutes,
                     RoutineType routineType, String routineDaysOfWeek, Integer routineIntervalDays) {
        this.memberId = memberId;
        this.title = title;
        this.originName = originName;
        this.originLat = originLat;
        this.originLng = originLng;
        this.originAddress = originAddress;
        this.originPlaceId = originPlaceId;
        this.originProvider = originProvider;
        this.destinationName = destinationName;
        this.destinationLat = destinationLat;
        this.destinationLng = destinationLng;
        this.destinationAddress = destinationAddress;
        this.destinationPlaceId = destinationPlaceId;
        this.destinationProvider = destinationProvider;
        this.userDepartureTime = userDepartureTime;
        this.arrivalTime = arrivalTime;
        this.reminderOffsetMinutes = reminderOffsetMinutes != null ? reminderOffsetMinutes : 5;
        this.routineType = routineType;
        this.routineDaysOfWeek = routineDaysOfWeek;
        this.routineIntervalDays = routineIntervalDays;
    }

    public static Schedule create(Long memberId, String title,
                                  String originName, BigDecimal originLat, BigDecimal originLng,
                                  String originAddress, String originPlaceId, PlaceProvider originProvider,
                                  String destinationName, BigDecimal destinationLat, BigDecimal destinationLng,
                                  String destinationAddress, String destinationPlaceId, PlaceProvider destinationProvider,
                                  OffsetDateTime userDepartureTime, OffsetDateTime arrivalTime,
                                  Integer reminderOffsetMinutes,
                                  RoutineType routineType, String routineDaysOfWeek, Integer routineIntervalDays) {
        return new Schedule(memberId, title,
                originName, originLat, originLng, originAddress, originPlaceId, originProvider,
                destinationName, destinationLat, destinationLng, destinationAddress, destinationPlaceId, destinationProvider,
                userDepartureTime, arrivalTime, reminderOffsetMinutes,
                routineType, routineDaysOfWeek, routineIntervalDays);
    }

    @PrePersist
    void prePersist() {
        if (this.scheduleUid == null) {
            this.scheduleUid = UlidGenerator.generate();
        }
    }

    public boolean belongsTo(Long memberId) {
        return Objects.equals(this.memberId, memberId);
    }

    public boolean hasCalculatedRoute() {
        return this.routeSummaryJson != null;
    }

    public Set<DayOfWeek> getDaysOfWeekSet() {
        if (routineDaysOfWeek == null || routineDaysOfWeek.isBlank()) return Set.of();
        return Arrays.stream(routineDaysOfWeek.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(DayOfWeekMapper::fromShortCode)
                .collect(Collectors.toSet());
    }

    /**
     * 명세 §5.4 부분 업데이트. {@code deletedAt != null} 시 SCHEDULE_NOT_FOUND — 유령 변경 차단.
     *
     * @return placeOrArrivalChanged — 출/도착지 또는 arrivalTime 변경 시 true (ODsay 재호출 trigger)
     */
    public boolean applyUpdate(String title,
                               PlaceUpdate origin,
                               PlaceUpdate destination,
                               OffsetDateTime userDepartureTime,
                               OffsetDateTime arrivalTime,
                               Integer reminderOffsetMinutes,
                               RoutineUpdate routine) {
        if (deletedAt != null) {
            throw new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        boolean placeOrArrivalChanged = false;

        if (title != null) this.title = title;

        if (origin != null) {
            this.originName = origin.name();
            this.originLat = origin.lat();
            this.originLng = origin.lng();
            this.originAddress = origin.address();
            this.originPlaceId = origin.placeId();
            this.originProvider = origin.provider();
            placeOrArrivalChanged = true;
        }

        if (destination != null) {
            this.destinationName = destination.name();
            this.destinationLat = destination.lat();
            this.destinationLng = destination.lng();
            this.destinationAddress = destination.address();
            this.destinationPlaceId = destination.placeId();
            this.destinationProvider = destination.provider();
            placeOrArrivalChanged = true;
        }

        if (userDepartureTime != null) this.userDepartureTime = userDepartureTime;

        if (arrivalTime != null) {
            this.arrivalTime = arrivalTime;
            placeOrArrivalChanged = true;
        }

        if (reminderOffsetMinutes != null) {
            this.reminderOffsetMinutes = reminderOffsetMinutes;
            recalculateReminderAt();
        }

        if (routine != null) {
            this.routineType = routine.type();
            this.routineDaysOfWeek = routine.daysOfWeek();
            this.routineIntervalDays = routine.intervalDays();
        }

        return placeOrArrivalChanged;
    }

    /**
     * RouteService 가 ODsay 호출 후 결과 반영. departureAdvice / reminderAt 자동 재계산.
     * {@code ON_TIME_WINDOW_MINUTES} 를 본 클래스 단독 소유 — 외부에서 임계값 재정의 시 silent drift.
     */
    public void updateRouteInfo(Integer estimatedDurationMinutes,
                                OffsetDateTime recommendedDepartureTime,
                                String routeSummaryJson,
                                OffsetDateTime routeCalculatedAt) {
        if (deletedAt != null) {
            throw new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.recommendedDepartureTime = recommendedDepartureTime;
        this.routeSummaryJson = routeSummaryJson;
        this.routeCalculatedAt = routeCalculatedAt;
        recalculateDepartureAdvice();
        recalculateReminderAt();
    }

    /**
     * userDepartureTime만 변경 시 — ODsay 재호출 X, departureAdvice만 재계산. 명세 §5.4 비고.
     */
    public void recalculateDepartureAdvice() {
        if (recommendedDepartureTime == null || userDepartureTime == null) {
            this.departureAdvice = null;
            return;
        }
        long diffMinutes = Duration.between(userDepartureTime, recommendedDepartureTime).toMinutes();
        if (diffMinutes < -ON_TIME_WINDOW_MINUTES) {
            this.departureAdvice = DepartureAdvice.LATER;
        } else if (diffMinutes > ON_TIME_WINDOW_MINUTES) {
            this.departureAdvice = DepartureAdvice.EARLIER;
        } else {
            this.departureAdvice = DepartureAdvice.ON_TIME;
        }
    }

    private void recalculateReminderAt() {
        if (recommendedDepartureTime != null && reminderOffsetMinutes != null) {
            this.reminderAt = recommendedDepartureTime.minusMinutes(reminderOffsetMinutes);
        } else {
            this.reminderAt = null;
        }
    }

    public void softDelete() {
        if (deletedAt == null) {
            this.deletedAt = OffsetDateTime.now(KST);
        }
    }

    /**
     * 명세 §9.1 — ONCE 일정 발송 직후 호출. {@code reminder_at = NULL} 로 재발송 방지. 멱등.
     */
    public void clearReminderAt() {
        if (deletedAt != null) {
            throw new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        this.reminderAt = null;
    }

    /**
     * 명세 §9.2 — 루틴 일정 다음 occurrence 로 갱신. ODsay 재호출 X
     * ({@code estimatedDurationMinutes} 마지막 호출값 그대로 사용).
     *
     * <p>{@code userDepartureTime} delta shift (v1.1.13): {@code arrivalTime} 변화량과 동일하게
     * 사용자 출발 의도 시각도 이동. 미동기화 시 {@code recalculateDepartureAdvice} 가 24h+ 차이로
     * 항상 {@code EARLIER} 가 되어 silent corruption 발생 → §6.1/§5.4 응답이 무의미해진다.
     */
    public void advanceToNextOccurrence(OffsetDateTime nextArrival) {
        if (deletedAt != null) {
            throw new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        if (userDepartureTime != null && this.arrivalTime != null) {
            Duration delta = Duration.between(this.arrivalTime, nextArrival);
            this.userDepartureTime = this.userDepartureTime.plus(delta);
        }
        this.arrivalTime = nextArrival;
        // estimatedDurationMinutes 는 OdsayRouteService.applyToSchedule 가 항상 non-null 로 호출하므로
        // reminderAt 이 박힌 dispatch 가능 schedule 은 항상 estimatedDurationMinutes 도 non-null. 다만 NPE
        // 방어 차원에서 가드만 둔다 (defensive).
        if (estimatedDurationMinutes != null) {
            this.recommendedDepartureTime = nextArrival.minusMinutes(estimatedDurationMinutes);
        }
        recalculateDepartureAdvice();
        recalculateReminderAt();
    }

    /** PATCH 부분 업데이트용 입력 DTO — 출/도착지 묶음. */
    public record PlaceUpdate(
            String name,
            BigDecimal lat,
            BigDecimal lng,
            String address,
            String placeId,
            PlaceProvider provider
    ) {}

    /** PATCH 부분 업데이트용 입력 DTO — 루틴 묶음. */
    public record RoutineUpdate(
            RoutineType type,
            String daysOfWeek,
            Integer intervalDays
    ) {}
}
