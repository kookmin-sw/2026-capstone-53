package com.todayway.backend.auth.service;

import com.todayway.backend.auth.domain.Member;
import com.todayway.backend.auth.domain.RefreshToken;
import com.todayway.backend.auth.dto.LoginRequest;
import com.todayway.backend.auth.dto.LoginResponse;
import com.todayway.backend.auth.dto.SignupRequest;
import com.todayway.backend.auth.dto.SignupResponse;
import com.todayway.backend.auth.repository.MemberRepository;
import com.todayway.backend.auth.repository.RefreshTokenRepository;
import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import com.todayway.backend.common.jwt.JwtProperties;
import com.todayway.backend.common.jwt.JwtProvider;
import com.todayway.backend.common.util.MemberIdFormatter;
import com.todayway.backend.common.util.Sha256Hasher;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;

    @Transactional
    public SignupResponse signup(SignupRequest req) {
        if (memberRepository.existsByLoginId(req.loginId())) {
            throw new BusinessException(ErrorCode.LOGIN_ID_DUPLICATED);
        }

        Member member;
        try {
            member = memberRepository.save(
                    Member.create(req.loginId(),
                                  passwordEncoder.encode(req.password()),
                                  req.nickname()));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.LOGIN_ID_DUPLICATED);
        }

        String accessToken = jwtProvider.issueAccessToken(member.getMemberUid());
        String refreshToken = jwtProvider.issueRefreshToken(member.getMemberUid());
        saveRefreshToken(member.getId(), refreshToken);

        return new SignupResponse(
                MemberIdFormatter.format(member.getMemberUid()),
                member.getLoginId(),
                member.getNickname(),
                accessToken,
                refreshToken
        );
    }

    @Transactional
    public LoginResponse login(LoginRequest req) {
        // loginId 존재 안 함과 password 불일치 모두 INVALID_CREDENTIALS로 통일 (api-spec §2.2, 사용자 존재 여부 leak 방지)
        Member member = memberRepository.findByLoginId(req.loginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(req.password(), member.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtProvider.issueAccessToken(member.getMemberUid());
        String refreshToken = jwtProvider.issueRefreshToken(member.getMemberUid());
        saveRefreshToken(member.getId(), refreshToken);

        return new LoginResponse(
                MemberIdFormatter.format(member.getMemberUid()),
                accessToken,
                refreshToken
        );
    }

    @Transactional
    public void logout(String memberUid) {
        Member member = memberRepository.findByMemberUid(memberUid)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        refreshTokenRepository.revokeAllActiveByMemberId(member.getId(), OffsetDateTime.now());
    }

    private void saveRefreshToken(Long memberId, String rawToken) {
        String tokenHash = Sha256Hasher.hash(rawToken);
        OffsetDateTime expiresAt = OffsetDateTime.now(KST)
                .plus(jwtProperties.refreshTokenExpirationDays(), ChronoUnit.DAYS);
        refreshTokenRepository.save(RefreshToken.create(memberId, tokenHash, expiresAt));
    }
}
