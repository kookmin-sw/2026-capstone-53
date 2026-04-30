package com.todayway.backend.common.web;

import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * @CurrentMember 가 붙은 String 파라미터에 SecurityContext의 raw memberUid 주입.
 * DB 호출 0회 — Authentication.getName() 만 호출. 멤버 존재 검증은 Service의 findByMemberUid 책임.
 * 명세 §1.7: JWT sub claim = prefix 없는 raw ULID 26자.
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
        if (auth == null || !(auth.getPrincipal() instanceof String memberUid) || memberUid.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return memberUid;
    }
}
