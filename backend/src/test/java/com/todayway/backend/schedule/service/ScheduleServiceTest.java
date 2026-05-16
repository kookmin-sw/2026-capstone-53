package com.todayway.backend.schedule.service;

import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import com.todayway.backend.member.domain.Member;
import com.todayway.backend.member.repository.MemberRepository;
import com.todayway.backend.route.RouteService;
import com.todayway.backend.schedule.domain.Schedule;
import com.todayway.backend.schedule.dto.ScheduleResponse;
import com.todayway.backend.schedule.dto.UpdateScheduleRequest;
import com.todayway.backend.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScheduleService.update의 PATCH validateTimes 분기 회귀 가드 (claude.ai PR #10 P1).
 * c9 (854dd8f)에서 도입한 3중 분기:
 *   - arrivalTime 포함 → validateTimes (NOW 검사 포함)
 *   - userDepartureTime만 → validateOrderOnly (NOW skip)
 *   - 둘 다 없음 → 검증 skip
 *
 * 누군가 분기를 무심코 합치면 silent regression — 이 테스트 3건이 컴파일/빌드 fail로 차단.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final String MEMBER_UID = "01HMM0123456789ABCDEFGHJK";
    private static final String SCHEDULE_UID = "01HSS0123456789ABCDEFGHJK";
    private static final Long MEMBER_ID = 1L;

    @Mock MemberRepository memberRepository;
    @Mock ScheduleRepository scheduleRepository;
    @Mock RouteService routeService;

    @InjectMocks ScheduleService scheduleService;

    @Test
    void update_whenScheduleIsPastAndOnlyTitleChanged_skipsNowValidation() {
        // 지난 일정 + title만 PATCH → NOW() 검사 skip + 200 (claude.ai P1 핵심 시나리오)
        Member member = mockMember(MEMBER_ID);
        Schedule pastSchedule = pastScheduleFixture(MEMBER_ID);
        when(memberRepository.findByMemberUid(MEMBER_UID)).thenReturn(Optional.of(member));
        when(scheduleRepository.findByScheduleUid(SCHEDULE_UID)).thenReturn(Optional.of(pastSchedule));

        UpdateScheduleRequest req = new UpdateScheduleRequest(
                "변경된 메모", null, null, null, null, null, null);

        ScheduleResponse resp = scheduleService.update(MEMBER_UID, SCHEDULE_UID, req);

        assertThat(resp.title()).isEqualTo("변경된 메모");
        verify(routeService, never()).refreshRouteSync(any(Schedule.class));
    }

    @Test
    void update_whenArrivalTimeIsBeforeNow_throwsValidationError() {
        // PATCH에 NOW() 이전 arrivalTime 명시 → validateTimes 발동 → 400
        Member member = mockMember(MEMBER_ID);
        Schedule schedule = futureScheduleFixture(MEMBER_ID);
        when(memberRepository.findByMemberUid(MEMBER_UID)).thenReturn(Optional.of(member));
        when(scheduleRepository.findByScheduleUid(SCHEDULE_UID)).thenReturn(Optional.of(schedule));

        OffsetDateTime pastArrival = OffsetDateTime.now(KST).minusHours(1);
        UpdateScheduleRequest req = new UpdateScheduleRequest(
                null, null, null, null, pastArrival, null, null);

        assertThatThrownBy(() -> scheduleService.update(MEMBER_UID, SCHEDULE_UID, req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void update_whenUserDepartureAfterArrival_throwsValidationError() {
        // userDepartureTime만 변경 + 순서 위반 (depart > arrival) → validateOrderOnly 발동 → 400
        Member member = mockMember(MEMBER_ID);
        Schedule schedule = futureScheduleFixture(MEMBER_ID);
        when(memberRepository.findByMemberUid(MEMBER_UID)).thenReturn(Optional.of(member));
        when(scheduleRepository.findByScheduleUid(SCHEDULE_UID)).thenReturn(Optional.of(schedule));

        // schedule.arrivalTime 이후로 userDepart 박음 → 순서 위반
        OffsetDateTime invalidDepart = schedule.getArrivalTime().plusMinutes(10);
        UpdateScheduleRequest req = new UpdateScheduleRequest(
                null, null, null, invalidDepart, null, null, null);

        assertThatThrownBy(() -> scheduleService.update(MEMBER_UID, SCHEDULE_UID, req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    // ───── helpers ─────

    private static Member mockMember(Long id) {
        Member m = mock(Member.class);
        when(m.getId()).thenReturn(id);
        return m;
    }

    private static Schedule pastScheduleFixture(Long memberId) {
        OffsetDateTime pastArrival = OffsetDateTime.now(KST).minusDays(1);
        return Schedule.create(
                memberId, "원래 메모",
                "출발", BigDecimal.ZERO, BigDecimal.ZERO, null, null, null,
                "도착", BigDecimal.ZERO, BigDecimal.ZERO, null, null, null,
                pastArrival.minusMinutes(30), pastArrival, 5,
                null, null, null,
                null, null
        );
    }

    private static Schedule futureScheduleFixture(Long memberId) {
        OffsetDateTime futureArrival = OffsetDateTime.now(KST).plusMinutes(60);
        return Schedule.create(
                memberId, "원래 메모",
                "출발", BigDecimal.ZERO, BigDecimal.ZERO, null, null, null,
                "도착", BigDecimal.ZERO, BigDecimal.ZERO, null, null, null,
                futureArrival.minusMinutes(30), futureArrival, 5,
                null, null, null,
                null, null
        );
    }
}
