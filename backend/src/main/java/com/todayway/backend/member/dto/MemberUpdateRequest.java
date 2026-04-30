package com.todayway.backend.member.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH /members/me 부분 업데이트 — null 필드는 무시 (의사결정 2).
 * 둘 다 null이면 success no-op (200 OK + 변경 없음, 명세 §3.2 강제 X).
 * loginId는 명세 §3.2상 수정 불가 — 본 record에 포함 X.
 */
public record MemberUpdateRequest(
        @Size(min = 2, max = 20, message = "nickname은 2~20자")
        String nickname,

        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[\\W_]).{8,72}$",
                 message = "password는 영문+숫자+특수문자 포함 8~72자")
        String password
) {}
