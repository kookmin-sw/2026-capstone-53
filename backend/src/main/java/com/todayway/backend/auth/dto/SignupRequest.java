package com.todayway.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank
        @Pattern(regexp = "^[a-zA-Z0-9]{4,20}$",
                 message = "loginId는 영문+숫자 4~20자")
        String loginId,

        @NotBlank
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[\\W_]).{8,72}$",
                 message = "password는 영문+숫자+특수문자 포함 8~72자")
        String password,

        @NotBlank
        @Size(min = 2, max = 20, message = "nickname은 2~20자")
        String nickname
) {}
