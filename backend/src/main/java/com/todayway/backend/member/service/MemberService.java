package com.todayway.backend.member.service;

import com.todayway.backend.auth.repository.RefreshTokenRepository;
import com.todayway.backend.member.domain.Member;
import com.todayway.backend.member.dto.MemberResponse;
import com.todayway.backend.member.dto.MemberUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberResponse getMe(Member member) {
        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse update(Member member, MemberUpdateRequest req) {
        if (req.nickname() != null) {
            member.updateNickname(req.nickname());
        }
        if (req.password() != null) {
            member.updatePasswordHash(passwordEncoder.encode(req.password()));
            // 의사결정 3 — password 변경 시 모든 활성 refresh token 폐기 (보안 ↑, 다른 디바이스 강제 로그아웃)
            refreshTokenRepository.revokeAllActiveByMemberId(member.getId(), OffsetDateTime.now(KST));
        }
        return MemberResponse.from(member);
    }

    @Transactional
    public void softDelete(Member member) {
        // 의사결정 4 (가-1) — Step 4 시점 가능한 cascade 2개:
        //   ✅ Member.deleted_at (자체)
        //   ✅ refresh_token.revoked_at 일괄
        //   ⏳ schedule.deleted_at — Step 5 진입 시 ScheduleRepository 주입 + cascade 추가
        //   ⏳ push_subscription.revoked_at — 이상진 Step 7 진입 시 추가
        member.softDelete();
        refreshTokenRepository.revokeAllActiveByMemberId(member.getId(), OffsetDateTime.now(KST));
    }
}
