package com.todayway.backend.member.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH /members/me 부분 업데이트 — null 필드는 무시 (의사결정 2).
 * 둘 다 null/생략이면 400 VALIDATION_ERROR (명세 §3.2 v1.1.6, 의사결정 5b — silent 보안 사고 방지).
 * loginId는 명세 §3.2상 수정 불가 — 본 record에 포함 X.
 */
public record MemberUpdateRequest(
        @Size(min = 2, max = 20, message = "nickname은 2~20자")
        String nickname,

        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[\\W_]).{8,72}$",
                 message = "password는 영문+숫자+특수문자 포함 8~72자")
        String password
) {
    @AssertTrue(message = "nickname 또는 password 중 적어도 하나는 명시해야 합니다")
    @JsonIgnore
    public boolean isAtLeastOneFieldPresent() {
        return nickname != null || password != null;
    }
}
