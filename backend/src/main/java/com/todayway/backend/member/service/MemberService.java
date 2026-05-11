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

    /**
     * 회원 탈퇴 — 명세 §3.3 v1.1.22 (이슈 #31). hard delete 로 row 자체 삭제.
     *
     * <p>cascade 동작 — DB FK ON DELETE CASCADE 가 자동 처리:
     * <ul>
     *   <li>{@code refresh_token} → 삭제 (활성 토큰 무효화 보장)</li>
     *   <li>{@code schedule} → 삭제 → {@code push_log.schedule_id SET NULL} (다른 회원 영향 X 위해 SET NULL)</li>
     *   <li>{@code push_subscription} → 삭제 → {@code push_log} CASCADE 삭제 (그 회원의 발송 이력 동반 삭제)</li>
     * </ul>
     *
     * <p>두 번째 호출은 {@code findByMemberUid} 가 empty → 401 UNAUTHORIZED (멱등성 §3.3 v1.1.7 정합).
     */
    @Transactional
    public void delete(String memberUid) {
        Member m = memberRepository.findByMemberUid(memberUid)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        memberRepository.delete(m);
        log.info("member hard delete — memberId={} (FK CASCADE 가 refresh_token / schedule / push_subscription 일괄 정리)",
                m.getId());
    }
}
