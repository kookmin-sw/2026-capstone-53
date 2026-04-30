package com.todayway.backend.schedule.service;

import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import com.todayway.backend.common.pagination.CursorRequest;
import com.todayway.backend.common.pagination.CursorResponse;
import com.todayway.backend.member.domain.Member;
import com.todayway.backend.member.repository.MemberRepository;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ScheduleService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String CURSOR_PREFIX = "id:";

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
                routineInterval(req.routineRule())
        );
        scheduleRepository.save(s);

        // ODsay 동기 호출 — graceful degradation은 RouteService 내부에서 처리 (false 반환)
        routeService.refreshRouteSync(s);

        return ScheduleResponse.from(s);
    }

    public ScheduleResponse get(String memberUid, String scheduleUid) {
        Long memberId = resolveMemberId(memberUid);
        Schedule s = findOwned(memberId, scheduleUid);
        return ScheduleResponse.from(s);
    }

    public CursorResponse<ScheduleListItem> list(String memberUid,
                                                 OffsetDateTime from,
                                                 OffsetDateTime to,
                                                 CursorRequest cursor) {
        Long memberId = resolveMemberId(memberUid);
        Long cursorId = decodeCursor(cursor.cursor());

        // limit + 1 조회 → hasMore 판정 + 마지막 row 제거
        List<Schedule> rows = scheduleRepository.findPage(memberId, from, to, cursorId,
                PageRequest.of(0, cursor.limit() + 1));
        boolean hasMore = rows.size() > cursor.limit();
        if (hasMore) rows = rows.subList(0, cursor.limit());

        String nextCursor = hasMore ? encodeCursor(rows.get(rows.size() - 1).getId()) : null;
        List<ScheduleListItem> items = rows.stream().map(ScheduleListItem::from).toList();
        return CursorResponse.of(items, nextCursor);
    }

    @Transactional
    public ScheduleResponse update(String memberUid, String scheduleUid, UpdateScheduleRequest req) {
        Long memberId = resolveMemberId(memberUid);
        Schedule s = findOwned(memberId, scheduleUid);

        validateTimes(
                req.userDepartureTime() != null ? req.userDepartureTime() : s.getUserDepartureTime(),
                req.arrivalTime() != null ? req.arrivalTime() : s.getArrivalTime()
        );

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
                        req.routineRule().intervalDays())
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

        return ScheduleResponse.from(s);
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
        if (userDepart == null || arrival == null) return;
        if (!userDepart.isBefore(arrival)) {
            // 명세 §5.1 에러: userDepartureTime > arrivalTime 또는 동일 시각
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (!arrival.isAfter(OffsetDateTime.now(KST))) {
            // 명세 §5.1 에러: arrivalTime <= NOW()
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private static String encodeCursor(Long id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((CURSOR_PREFIX + id).getBytes(StandardCharsets.UTF_8));
    }

    private static Long decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (!decoded.startsWith(CURSOR_PREFIX)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
            return Long.parseLong(decoded.substring(CURSOR_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
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
}
