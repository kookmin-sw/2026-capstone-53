package com.todayway.backend.auth.service;

import com.todayway.backend.auth.dto.SignupRequest;
import com.todayway.backend.member.domain.Member;
import com.todayway.backend.member.repository.MemberRepository;
import com.todayway.backend.auth.repository.RefreshTokenRepository;
import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import com.todayway.backend.common.jwt.JwtProperties;
import com.todayway.backend.common.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock MemberRepository memberRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtProvider jwtProvider;
    @Mock JwtProperties jwtProperties;

    @InjectMocks AuthService authService;

    @Test
    void signup_save단계_DataIntegrityViolation은_LOGIN_ID_DUPLICATED로_변환된다() {
        // 사전 check 통과 — race window: 다른 트랜잭션이 그 사이 INSERT
        when(memberRepository.existsByLoginId("chanwoo90")).thenReturn(false);
        when(memberRepository.save(any(Member.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate entry 'chanwoo90' for key 'uq_member_login'"));

        SignupRequest req = new SignupRequest("chanwoo90", "P@ssw0rd!", "찬우");

        assertThatThrownBy(() -> authService.signup(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.LOGIN_ID_DUPLICATED));
    }
}
