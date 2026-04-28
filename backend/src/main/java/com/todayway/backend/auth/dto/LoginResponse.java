package com.todayway.backend.auth.dto;

public record LoginResponse(
        String memberId,
        String accessToken,
        String refreshToken
) {}
