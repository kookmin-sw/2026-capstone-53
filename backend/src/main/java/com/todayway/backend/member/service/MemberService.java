package com.todayway.backend.member.service;

import com.todayway.backend.auth.repository.RefreshTokenRepository;
import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import com.todayway.backend.member.domain.Member;
import com.todayway.backend.member.dto.MemberResponse;
import com.todayway.backend.member.dto.MemberUpdateRequest;
import com.todayway.backend.member.repository.MemberRepository;
import com.todayway.backend.push.repository.PushSubscriptionRepository;
import com.todayway.backend.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class MemberService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ScheduleRepository scheduleRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberResponse getMe(String memberUid) {
        Member m = memberRepository.findByMemberUid(memberUid)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return MemberResponse.from(m);
    }

    @Transactional
    public MemberResponse update(String memberUid, MemberUpdateRequest req) {
        Member m = memberRepository.findByMemberUid(memberUid)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        if (req.nickname() != null) {
            m.updateNickname(req.nickname());
        }
        if (req.password() != null) {
            m.updatePasswordHash(passwordEncoder.encode(req.password()));
            // 의사결정 3 — password 변경 시 모든 활성 refresh token 폐기 (보안 ↑, 다른 디바이스 강제 로그아웃)
            int revoked = refreshTokenRepository.revokeAllActiveByMemberId(m.getId(), OffsetDateTime.now(KST));
            log.info("revoked {} active refresh tokens for memberId={} (password change)", revoked, m.getId());
        }
        return MemberResponse.from(m);
    }

    @Transactional
    public void softDelete(String memberUid) {
        // soft-delete cascade 는 코드 레벨에서만 보장 — DB FK CASCADE 는 hard-delete 시에만 동작.
        Member m = memberRepository.findByMemberUid(memberUid)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        m.softDelete();
        OffsetDateTime now = OffsetDateTime.now(KST);
        int revokedTokens = refreshTokenRepository.revokeAllActiveByMemberId(m.getId(), now);
        int deletedSchedules = scheduleRepository.softDeleteByMemberId(m.getId(), now);
        int revokedSubscriptions = pushSubscriptionRepository.revokeAllByMemberId(m.getId(), now);
        log.info("member soft delete cascade — memberId={} : refresh tokens={}, schedules={}, push subscriptions={}",
                m.getId(), revokedTokens, deletedSchedules, revokedSubscriptions);
    }
}
