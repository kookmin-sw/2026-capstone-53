package com.todayway.backend.auth.service;

import com.todayway.backend.auth.domain.RefreshToken;
import com.todayway.backend.member.domain.Member;
import com.todayway.backend.auth.dto.LoginRequest;
import com.todayway.backend.auth.dto.LoginResponse;
import com.todayway.backend.auth.dto.SignupRequest;
import com.todayway.backend.auth.dto.SignupResponse;
import com.todayway.backend.auth.repository.RefreshTokenRepository;
import com.todayway.backend.member.repository.MemberRepository;
import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import com.todayway.backend.common.jwt.JwtProperties;
import com.todayway.backend.common.jwt.JwtProvider;
import com.todayway.backend.common.util.Sha256Hasher;
import com.todayway.backend.common.web.IdPrefixes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
                IdPrefixes.MEMBER + member.getMemberUid(),
                member.getLoginId(),
                member.getNickname(),
                accessToken,
                refreshToken
        );
    }

    @Transactional
    public LoginResponse login(LoginRequest req) {
        // v1.1.40 — 슬랙 #5 (회원가입→로그아웃→재로그인 안 됨) 디버그용. FE 합동 재현 시
        // payload 의 length / null 여부로 자동완성/IME silent 공백 등을 식별. PII 차단 — loginId
        // 와 password length 만, 평문 X. 데모 직후 제거 권고 (T2 별 task 검증 완료 후).
        log.debug("login attempt: loginId={}, providedPwLen={}",
                req.loginId(),
                req.password() == null ? -1 : req.password().length());

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
                IdPrefixes.MEMBER + member.getMemberUid(),
                accessToken,
                refreshToken
        );
    }

    @Transactional
    public void logout(String refreshToken) {
        // 명세 §2.3 — 전달된 refresh 토큰 1개만 폐기. 미존재/이미 폐기는 silent noop (멱등, RFC 7009 정신).
        String tokenHash = Sha256Hasher.hash(refreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(RefreshToken::revoke);
    }

    private void saveRefreshToken(Long memberId, String rawToken) {
        String tokenHash = Sha256Hasher.hash(rawToken);
        OffsetDateTime expiresAt = OffsetDateTime.now(KST)
                .plus(jwtProperties.refreshTokenExpirationDays(), ChronoUnit.DAYS);
        refreshTokenRepository.save(RefreshToken.create(memberId, tokenHash, expiresAt));
    }
}
