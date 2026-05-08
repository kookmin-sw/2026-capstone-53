package com.todayway.backend.common.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentMember {

    /**
     * {@code true} (기본): 미인증 시 {@code UNAUTHORIZED} 401 — 모든 인증 필요 endpoint 의 디폴트.
     * {@code false}: 미인증 시 {@code null} 주입 — 명세 §4.1 {@code GET /main} 같은
     * "게스트 허용 (인증 시 추가 정보)" endpoint 전용.
     */
    boolean required() default true;
}
