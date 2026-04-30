package com.todayway.backend.member.service;

import com.todayway.backend.auth.repository.RefreshTokenRepository;
import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import com.todayway.backend.member.domain.Member;
import com.todayway.backend.member.dto.MemberResponse;
import com.todayway.backend.member.dto.MemberUpdateRequest;
import com.todayway.backend.member.repository.MemberRepository;
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
        // 의사결정 4 (가-1) — Step 4 시점 가능한 cascade 2개:
        //   ✅ Member.deleted_at (자체)
        //   ✅ refresh_token.revoked_at 일괄
        //   ⏳ schedule.deleted_at — Step 5 진입 시 ScheduleRepository 주입 + cascade 추가
        //   ⏳ push_subscription.revoked_at — 이상진 Step 7 진입 시 추가
        Member m = memberRepository.findByMemberUid(memberUid)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        m.softDelete();
        int revoked = refreshTokenRepository.revokeAllActiveByMemberId(m.getId(), OffsetDateTime.now(KST));
        log.info("revoked {} active refresh tokens for memberId={} (soft delete)", revoked, m.getId());
    }
}
