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

    public MemberResponse getMe(Member member) {
        // getMe는 단순 응답 — detached 객체의 getter만 호출하므로 재조회 불필요
        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse update(Member member, MemberUpdateRequest req) {
        // CurrentMemberArgumentResolver가 반환한 Member는 트랜잭션 외부에서 조회된 detached entity.
        // 변경을 dirty marking으로 반영시키려면 트랜잭션 내에서 재조회해 managed 상태로 attach.
        Member managed = memberRepository.findById(member.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (req.nickname() != null) {
            managed.updateNickname(req.nickname());
        }
        if (req.password() != null) {
            managed.updatePasswordHash(passwordEncoder.encode(req.password()));
            // 의사결정 3 — password 변경 시 모든 활성 refresh token 폐기 (보안 ↑, 다른 디바이스 강제 로그아웃)
            int revoked = refreshTokenRepository.revokeAllActiveByMemberId(managed.getId(), OffsetDateTime.now(KST));
            log.info("revoked {} active refresh tokens for memberId={} (password change)", revoked, managed.getId());
        }
        return MemberResponse.from(managed);
    }

    @Transactional
    public void softDelete(Member member) {
        // 의사결정 4 (가-1) — Step 4 시점 가능한 cascade 2개:
        //   ✅ Member.deleted_at (자체)
        //   ✅ refresh_token.revoked_at 일괄
        //   ⏳ schedule.deleted_at — Step 5 진입 시 ScheduleRepository 주입 + cascade 추가
        //   ⏳ push_subscription.revoked_at — 이상진 Step 7 진입 시 추가
        Member managed = memberRepository.findById(member.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        managed.softDelete();
        int revoked = refreshTokenRepository.revokeAllActiveByMemberId(managed.getId(), OffsetDateTime.now(KST));
        log.info("revoked {} active refresh tokens for memberId={} (soft delete)", revoked, managed.getId());
    }
}
