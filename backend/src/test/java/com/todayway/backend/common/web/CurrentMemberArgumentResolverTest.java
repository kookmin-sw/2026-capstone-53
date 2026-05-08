package com.todayway.backend.common.web;

import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Resolver 단위 테스트. SecurityFilterChain 의 인증 보장과 별도로 Resolver 자체의 안전망 검증.
 */
class CurrentMemberArgumentResolverTest {

    private final CurrentMemberArgumentResolver resolver = new CurrentMemberArgumentResolver();

    @SuppressWarnings("unused")
    private static class TargetMethods {
        void requiredMethod(@CurrentMember String memberUid) {
        }

        void optionalMethod(@CurrentMember(required = false) String memberUid) {
        }
    }

    private static MethodParameter requiredParam() throws NoSuchMethodException {
        Method m = TargetMethods.class.getDeclaredMethod("requiredMethod", String.class);
        return new MethodParameter(m, 0);
    }

    private static MethodParameter optionalParam() throws NoSuchMethodException {
        Method m = TargetMethods.class.getDeclaredMethod("optionalMethod", String.class);
        return new MethodParameter(m, 0);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveArgument_whenAuthenticationNull_throwsUnauthorized() throws Exception {
        SecurityContextHolder.clearContext();
        MethodParameter param = requiredParam();
        assertThatThrownBy(() -> resolver.resolveArgument(param, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void resolveArgument_whenPrincipalNotString_throwsUnauthorized() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new Object(), null, List.of()));
        MethodParameter param = requiredParam();
        assertThatThrownBy(() -> resolver.resolveArgument(param, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void resolveArgument_whenMemberUidBlank_throwsUnauthorized() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("   ", null, List.of()));
        MethodParameter param = requiredParam();
        assertThatThrownBy(() -> resolver.resolveArgument(param, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void resolveArgument_whenValidPrincipal_returnsMemberUid() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("01HAA0123456789ABCDEFGHJK", null, List.of()));
        MethodParameter param = requiredParam();
        Object result = resolver.resolveArgument(param, null, null, null);
        assertThat(result).isEqualTo("01HAA0123456789ABCDEFGHJK");
    }

    @Test
    void resolveArgument_whenOptional_andUnauthenticated_returnsNull() throws Exception {
        // 명세 §4.1 — GET /main 게스트 허용. @CurrentMember(required=false) 면 null 주입.
        SecurityContextHolder.clearContext();
        MethodParameter param = optionalParam();
        Object result = resolver.resolveArgument(param, null, null, null);
        assertThat(result).isNull();
    }

    @Test
    void resolveArgument_whenOptional_andAuthenticated_returnsMemberUid() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("01HAA0123456789ABCDEFGHJK", null, List.of()));
        MethodParameter param = optionalParam();
        Object result = resolver.resolveArgument(param, null, null, null);
        assertThat(result).isEqualTo("01HAA0123456789ABCDEFGHJK");
    }

    @Test
    void resolveArgument_whenAnonymousToken_andOptional_returnsNull() throws Exception {
        // Spring Security 의 AnonymousAuthenticationFilter 가 미인증 요청에 박는 토큰 — principal 이
        // 비어있지 않은 String "anonymousUser" 라 단순 instanceof 검사로는 통과한다. instanceof
        // AnonymousAuthenticationToken 명시 제외 가드의 회귀 검출용.
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        MethodParameter param = optionalParam();
        Object result = resolver.resolveArgument(param, null, null, null);
        assertThat(result).isNull();
    }

    @Test
    void resolveArgument_whenAnonymousToken_andRequired_throwsUnauthorized() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        MethodParameter param = requiredParam();
        assertThatThrownBy(() -> resolver.resolveArgument(param, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
