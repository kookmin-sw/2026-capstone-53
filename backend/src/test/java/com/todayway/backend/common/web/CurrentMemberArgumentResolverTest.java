package com.todayway.backend.common.web;

import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Resolver 단위 테스트 (claude.ai PR #7 리뷰 P2 흡수).
 * SecurityFilterChain의 인증 보장과 별도로 Resolver 자체의 안전망 검증.
 */
class CurrentMemberArgumentResolverTest {

    private final CurrentMemberArgumentResolver resolver = new CurrentMemberArgumentResolver();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveArgument_whenAuthenticationNull_throwsUnauthorized() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void resolveArgument_whenPrincipalNotString_throwsUnauthorized() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new Object(), null, List.of()));
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void resolveArgument_whenMemberUidBlank_throwsUnauthorized() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("   ", null, List.of()));
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void resolveArgument_whenValidPrincipal_returnsMemberUid() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("01HAA0123456789ABCDEFGHJK", null, List.of()));
        Object result = resolver.resolveArgument(null, null, null, null);
        assertThat(result).isEqualTo("01HAA0123456789ABCDEFGHJK");
    }
}
