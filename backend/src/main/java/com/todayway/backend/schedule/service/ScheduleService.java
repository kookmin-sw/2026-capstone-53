package com.todayway.backend.schedule.service;

import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import com.todayway.backend.common.pagination.CursorRequest;
import com.todayway.backend.common.pagination.CursorResponse;
import com.todayway.backend.member.domain.Member;
import com.todayway.backend.member.repository.MemberRepository;
import com.todayway.backend.route.RouteResponse;
import com.todayway.backend.route.RouteService;
import com.todayway.backend.schedule.domain.Schedule;
import com.todayway.backend.schedule.dto.CreateScheduleRequest;
import com.todayway.backend.schedule.dto.PlaceDto;
import com.todayway.backend.schedule.dto.RoutineRuleDto;
import com.todayway.backend.schedule.dto.ScheduleListItem;
import com.todayway.backend.schedule.dto.ScheduleResponse;
import com.todayway.backend.schedule.dto.UpdateScheduleRequest;
import com.todayway.backend.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ScheduleService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String CURSOR_PREFIX_V2 = "v2:";
    private static final String CURSOR_DELIMITER = "|";

    private final MemberRepository memberRepository;
    private final ScheduleRepository scheduleRepository;
    private final RouteService routeService;

    @Transactional
    public ScheduleResponse create(String memberUid, CreateScheduleRequest req) {
        Long memberId = resolveMemberId(memberUid);
        validateTimes(req.userDepartureTime(), req.arrivalTime());

        Schedule s = Schedule.create(
                memberId, req.title(),
                req.origin().name(), req.origin().lat(), req.origin().lng(),
                req.origin().address(), req.origin().placeId(), req.origin().provider(),
                req.destination().name(), req.destination().lat(), req.destination().lng(),
                req.destination().address(), req.destination().placeId(), req.destination().provider(),
                req.userDepartureTime(), req.arrivalTime(),
                req.reminderOffsetMinutes(),
                routineType(req.routineRule()),
                routineDaysCsv(req.routineRule()),
                routineInterval(req.routineRule()),
                routineStartDate(req.routineRule()),
                routineEndDate(req.routineRule())
        );
        scheduleRepository.save(s);

        // ODsay 동기 호출 — graceful degradation은 RouteService 내부에서 처리 (false 반환)
        routeService.refreshRouteSync(s);

        // v1.1.40 T5 — 사용자가 userDepartureTime 미입력 시 BE 가 recommendedDepartureTime 으로 자동 채움.
        // ODsay 실패 (recommended=null) 케이스는 noop — userDepartureTime null 유지.
        s.autoFillUserDepartureTime(s.getRecommendedDepartureTime());

        // v1.1.40 R4 + R4-Q2 — reminderAt clamp/skip 가드 (등록 시점)
        ClampResult clamp = applyReminderAtGuard(s);

        return ScheduleResponse.withClampMeta(s, clamp.clamped(), clamp.skipped());
    }

    public ScheduleResponse get(String memberUid, String scheduleUid) {
        Long memberId = resolveMemberId(memberUid);
        Schedule s = findOwned(memberId, scheduleUid);
        return ScheduleResponse.from(s);
    }

    /**
     * 명세 §6.1 — 일정 경로 조회. 소유자 검증 + ODsay 캐시/호출 위임.
     * <p>{@code @Transactional} (rw) — cache miss 시 {@code RouteService.getRoute}가
     * {@code Schedule.updateRouteInfo}로 entity 갱신. 같은 트랜잭션 안에서 fetch +
     * getRoute 호출이라 dirty checking 정상 동작.
     *
     * @throws com.todayway.backend.common.exception.BusinessException
     *         {@code SCHEDULE_NOT_FOUND}(404), {@code FORBIDDEN_RESOURCE}(403),
     *         {@code EXTERNAL_*}(502/503/504, RouteService에서 매핑)
     */
    @Transactional
    public RouteResponse getRouteForOwned(String memberUid, String scheduleUid, boolean forceRefresh) {
        Long memberId = resolveMemberId(memberUid);
        Schedule s = findOwned(memberId, scheduleUid);
        return routeService.getRoute(s, forceRefresh);
    }

    public CursorResponse<ScheduleListItem> list(String memberUid,
                                                 OffsetDateTime from,
                                                 OffsetDateTime to,
                                                 CursorRequest cursor) {
        Long memberId = resolveMemberId(memberUid);
        CursorKey key = decodeCursor(cursor.cursor());

        // limit + 1 조회 → hasMore 판정 + 마지막 row 제거
        List<Schedule> rows = scheduleRepository.findPage(
                memberId, from, to,
                key != null ? key.arrivalTime() : null,
                key != null ? key.id() : null,
                PageRequest.of(0, cursor.limit() + 1));
        boolean hasMore = rows.size() > cursor.limit();
        if (hasMore) rows = rows.subList(0, cursor.limit());

        String nextCursor = null;
        if (hasMore) {
            Schedule last = rows.get(rows.size() - 1);
            nextCursor = encodeCursor(last.getArrivalTime(), last.getId());
        }
        List<ScheduleListItem> items = rows.stream().map(ScheduleListItem::from).toList();
        return CursorResponse.of(items, nextCursor);
    }

    @Transactional
    public ScheduleResponse update(String memberUid, String scheduleUid, UpdateScheduleRequest req) {
        Long memberId = resolveMemberId(memberUid);
        Schedule s = findOwned(memberId, scheduleUid);

        // 명세 §5.4 v1.1.8 — arrivalTime이 PATCH에 포함된 경우만 NOW() 검사 (claude.ai PR #10 P1).
        // 지난 일정에 title 등 메모 편집 시 NOW() 검사로 fail 방지.
        if (req.arrivalTime() != null) {
            OffsetDateTime newDepart = req.userDepartureTime() != null
                    ? req.userDepartureTime() : s.getUserDepartureTime();
            validateTimes(newDepart, req.arrivalTime());
        } else if (req.userDepartureTime() != null) {
            // userDepartureTime만 변경 — 순서 검증만 (NOW() skip)
            validateOrderOnly(req.userDepartureTime(), s.getArrivalTime());
        }

        Schedule.PlaceUpdate originUpdate = req.origin() != null
                ? new Schedule.PlaceUpdate(
                        req.origin().name(), req.origin().lat(), req.origin().lng(),
                        req.origin().address(), req.origin().placeId(), req.origin().provider())
                : null;
        Schedule.PlaceUpdate destinationUpdate = req.destination() != null
                ? new Schedule.PlaceUpdate(
                        req.destination().name(), req.destination().lat(), req.destination().lng(),
                        req.destination().address(), req.destination().placeId(), req.destination().provider())
                : null;
        Schedule.RoutineUpdate routineUpdate = req.routineRule() != null
                ? new Schedule.RoutineUpdate(
                        req.routineRule().type(),
                        routineDaysCsv(req.routineRule()),
                        req.routineRule().intervalDays(),
                        req.routineRule().startDate(),
                        req.routineRule().endDate())
                : null;

        boolean placeOrArrivalChanged = s.applyUpdate(
                req.title(), originUpdate, destinationUpdate,
                req.userDepartureTime(), req.arrivalTime(),
                req.reminderOffsetMinutes(), routineUpdate
        );

        if (placeOrArrivalChanged) {
            // 명세 §5.4 — 출/도착지 또는 arrivalTime 변경 시 ODsay 재호출
            routeService.refreshRouteSync(s);
        } else if (req.userDepartureTime() != null) {
            // 명세 §5.4 비고 — userDepartureTime만 변경 시 ODsay 재호출 X, advice만 재계산
            s.recalculateDepartureAdvice();
        }

        // v1.1.40 R4 + R4-Q2 — reminderAt clamp/skip 가드 (수정 시점)
        ClampResult clamp = applyReminderAtGuard(s);

        return ScheduleResponse.withClampMeta(s, clamp.clamped(), clamp.skipped());
    }

    @Transactional
    public void delete(String memberUid, String scheduleUid) {
        Long memberId = resolveMemberId(memberUid);
        Schedule s = findOwned(memberId, scheduleUid);
        s.softDelete();
    }

    // ───── private helpers ─────

    private Long resolveMemberId(String memberUid) {
        return memberRepository.findByMemberUid(memberUid)
                .map(Member::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private Schedule findOwned(Long memberId, String scheduleUid) {
        Schedule s = scheduleRepository.findByScheduleUid(scheduleUid)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
        if (!s.belongsTo(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_RESOURCE);
        }
        return s;
    }

    private void validateTimes(OffsetDateTime userDepart, OffsetDateTime arrival) {
        if (arrival == null) return;
        // v1.1.40 T5 — userDepart null 허용 (BE 자동 계산). null 시 순서 검증 skip.
        if (userDepart != null && !userDepart.isBefore(arrival)) {
            // 명세 §5.1 에러: userDepartureTime > arrivalTime 또는 동일 시각
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (!arrival.isAfter(OffsetDateTime.now(KST))) {
            // 명세 §5.1 에러: arrivalTime <= NOW()
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    /**
     * userDepartureTime만 변경된 PATCH 시 호출 — 순서만 검증 (NOW() skip).
     * 명세 §5.4 v1.1.8 — 지난 일정 메모 편집 허용 (claude.ai PR #10 P1).
     */
    private void validateOrderOnly(OffsetDateTime userDepart, OffsetDateTime arrival) {
        if (userDepart == null || arrival == null) return;
        if (!userDepart.isBefore(arrival)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    /**
     * cursor 인코딩 — 이슈 #15 합성키 (arrivalTime, id) 기준.
     * <p>arrivalTime 은 UTC 로 정규화 (DB 영속화 형식과 일치, 클라이언트 무관 — opaque token).
     * v1 ({@code id:N}) cursor 는 이번 fix 로 폐기, 신규 prefix {@code v2:} 도입.
     */
    private static String encodeCursor(OffsetDateTime arrivalTime, Long id) {
        String payload = CURSOR_PREFIX_V2
                + arrivalTime.withOffsetSameInstant(ZoneOffset.UTC)
                + CURSOR_DELIMITER + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static CursorKey decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (!decoded.startsWith(CURSOR_PREFIX_V2)) {
            // 구버전 ({@code id:N}) cursor 또는 변조 — 명세 §1.5 opaque 정책상 1페이지부터 재요청 강제
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String body = decoded.substring(CURSOR_PREFIX_V2.length());
        int sep = body.indexOf(CURSOR_DELIMITER);
        if (sep <= 0 || sep == body.length() - 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        try {
            OffsetDateTime arrival = OffsetDateTime.parse(body.substring(0, sep));
            Long id = Long.parseLong(body.substring(sep + 1));
            return new CursorKey(arrival, id);
        } catch (DateTimeParseException | NumberFormatException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private record CursorKey(OffsetDateTime arrivalTime, Long id) {}

    /**
     * v1.1.40 R4 + R4-Q2 — 등록/수정 시 {@code reminderAt} 가드.
     *
     * <p>현 BE 의 {@code reminderAt} 은 {@code recommendedDepartureTime - reminderOffsetMinutes} 로
     * 계산되는데, 가까운 일정 + 큰 offset 조합 시 이미 과거 시각이 되어 dispatcher 5분 폴링
     * 윈도우 (NOW()-5min ~ NOW(), §9.1) 밖이면 silent 누락. T6 default = 30 채택 후 흔해질 시나리오.
     *
     * <p>3가지 분기:
     * <ul>
     *   <li>정상: {@code floor < reminderAt ≤ ceiling} — 그대로 두고 clamped/skipped 둘 다 false</li>
     *   <li>clamp: {@code reminderAt < floor} (= NOW()+60s) — floor 로 override, clamped=true</li>
     *   <li>skip: {@code ceiling} (= arrivalTime-1min) {@code < floor} — arrivalTime 자체가 너무
     *       가까워 의미 있는 알림 불가능. reminderAt=null override, skipped=true</li>
     * </ul>
     */
    private ClampResult applyReminderAtGuard(Schedule s) {
        OffsetDateTime reminderAt = s.getReminderAt();
        if (reminderAt == null) {
            // ODsay 실패 / 자동 계산 미적용 등 — 가드 무의미
            return ClampResult.none();
        }
        OffsetDateTime now = OffsetDateTime.now(KST);
        OffsetDateTime floor = now.plusSeconds(60);
        OffsetDateTime ceiling = s.getArrivalTime() != null
                ? s.getArrivalTime().minusMinutes(1) : null;

        if (ceiling != null && ceiling.isBefore(floor)) {
            // arrivalTime 자체가 너무 가까움 — 알림 불가능
            s.overrideReminderAt(null);
            return ClampResult.skipped();
        }
        if (reminderAt.isBefore(floor)) {
            s.overrideReminderAt(floor);
            return ClampResult.clamped();
        }
        // 정상 (또는 ceiling 가드 무관 케이스 — arrivalTime null 흐름)
        return ClampResult.none();
    }

    private record ClampResult(boolean clamped, boolean skipped) {
        static ClampResult none()    { return new ClampResult(false, false); }
        static ClampResult clamped() { return new ClampResult(true,  false); }
        static ClampResult skipped() { return new ClampResult(false, true);  }
    }

    private static com.todayway.backend.schedule.domain.RoutineType routineType(RoutineRuleDto r) {
        return r != null ? r.type() : null;
    }

    private static String routineDaysCsv(RoutineRuleDto r) {
        return r != null ? r.toCsv() : null;
    }

    private static Integer routineInterval(RoutineRuleDto r) {
        return r != null ? r.intervalDays() : null;
    }

    private static LocalDate routineStartDate(RoutineRuleDto r) {
        return r != null ? r.startDate() : null;
    }

    private static LocalDate routineEndDate(RoutineRuleDto r) {
        return r != null ? r.endDate() : null;
    }
}
