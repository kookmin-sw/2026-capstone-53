package com.todayway.backend.map.service;

import com.todayway.backend.map.config.MapConfigProperties;
import com.todayway.backend.map.dto.Coordinate;
import com.todayway.backend.map.dto.MainResponse;
import com.todayway.backend.map.dto.NearestScheduleDto;
import com.todayway.backend.member.domain.Member;
import com.todayway.backend.member.repository.MemberRepository;
import com.todayway.backend.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 명세 §4.1 — {@code GET /main} 메인 화면 데이터 조회 (게스트 허용).
 *
 * <p>{@code memberUid} 가 {@code null} 인 게스트 호출은 nearestSchedule=null 로 응답.
 * 인증된 호출은 시간상 가장 가까운 미래 일정 한 건을 노출.
 *
 * <p>mapCenter 결정 우선순위 (명세 §4.1 비고):
 * <ol>
 *   <li>query lat/lng</li>
 *   <li>nearestSchedule.origin</li>
 *   <li>{@link MapConfigProperties} default-center (예: 서울시청)</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MainService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final ScheduleRepository scheduleRepository;
    private final MapConfigProperties mapConfigProperties;

    public MainResponse compose(String memberUid, Double lat, Double lng) {
        NearestScheduleDto nearest = resolveNearest(memberUid);
        Coordinate mapCenter = decideMapCenter(lat, lng, nearest);
        return new MainResponse(nearest, mapCenter);
    }

    private NearestScheduleDto resolveNearest(String memberUid) {
        if (memberUid == null) {
            return null;
        }
        // 탈퇴 회원의 토큰이 살아있는 경우 — 명세 §1.7 / §3.3 정합으로 nearestSchedule = null
        // (UNAUTHORIZED 던지지 않고 graceful 게스트 처리. 로그인 필요 흐름은 다른 endpoint 가 담당).
        // 단 silent 처리는 token-revocation 파이프라인 고장을 가리므로 WARN 로깅으로 신호 보존.
        Long memberId = memberRepository.findByMemberUid(memberUid)
                .map(Member::getId)
                .orElse(null);
        if (memberId == null) {
            log.warn("Authenticated /main with valid JWT but member not found — graceful guest fallback. memberUid={}",
                    memberUid);
            return null;
        }
        return scheduleRepository
                .findFirstByMemberIdAndArrivalTimeAfterOrderByArrivalTimeAsc(memberId, OffsetDateTime.now(KST))
                .map(NearestScheduleDto::from)
                .orElse(null);
    }

    private Coordinate decideMapCenter(Double lat, Double lng, NearestScheduleDto nearest) {
        if (lat != null && lng != null) {
            return new Coordinate(lat, lng);
        }
        if (nearest != null) {
            return new Coordinate(nearest.origin().lat(), nearest.origin().lng());
        }
        MapConfigProperties.DefaultCenter dc = mapConfigProperties.getDefaultCenter();
        return new Coordinate(dc.getLat(), dc.getLng());
    }
}
