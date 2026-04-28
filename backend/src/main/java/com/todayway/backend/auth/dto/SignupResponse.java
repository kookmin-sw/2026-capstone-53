package com.todayway.backend.auth.dto;

public record SignupResponse(
        String memberId,
        String loginId,
        String nickname,
        String accessToken,
        String refreshToken
) {}
