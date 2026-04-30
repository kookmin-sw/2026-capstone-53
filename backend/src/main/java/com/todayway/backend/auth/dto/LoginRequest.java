package com.todayway.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String loginId,
        @NotBlank String password
) {
    @Override
    public String toString() {
        return "LoginRequest{loginId=%s, password=***}".formatted(loginId);
    }
}
