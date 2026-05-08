package com.todayway.backend.common.web;

import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link CurrentMember} 가 붙은 String 파라미터에 SecurityContext 의 raw memberUid 주입.
 * DB 호출 0회 — Authentication.getPrincipal() 만 사용. 멤버 존재 검증은 Service 의 findByMemberUid 책임.
 * 명세 §1.7: JWT sub claim = prefix 없는 raw ULID 26자.
 *
 * <p>{@code @CurrentMember(required = false)} 인 경우 미인증 시 {@code null} 주입 — 명세 §4.1
 * {@code GET /main} 게스트 허용 endpoint 전용. 디폴트 ({@code required = true}) 는 미인증 시 401.
 */
@Component
public class CurrentMemberArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentMember.class)
                && parameter.getParameterType().equals(String.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // Spring Security 의 AnonymousAuthenticationFilter 가 미인증 요청에 principal="anonymousUser"
        // String 토큰을 박는다. 단순 `instanceof String` 검사만으로는 그 문자열이 valid memberUid
        // 처럼 통과하므로 AnonymousAuthenticationToken 을 명시적으로 제외해야 게스트 흐름이 발화한다.
        boolean authenticated = auth != null
                && !(auth instanceof AnonymousAuthenticationToken)
                && auth.getPrincipal() instanceof String memberUid
                && !memberUid.isBlank();
        if (authenticated) {
            return ((String) auth.getPrincipal());
        }
        CurrentMember annotation = parameter.getParameterAnnotation(CurrentMember.class);
        if (annotation != null && !annotation.required()) {
            return null;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
