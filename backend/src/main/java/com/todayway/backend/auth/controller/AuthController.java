package com.todayway.backend.auth.controller;

import com.todayway.backend.auth.dto.LoginRequest;
import com.todayway.backend.auth.dto.LoginResponse;
import com.todayway.backend.auth.dto.SignupRequest;
import com.todayway.backend.auth.dto.SignupResponse;
import com.todayway.backend.auth.service.AuthService;
import com.todayway.backend.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@RequestBody @Valid SignupRequest req) {
        SignupResponse res = authService.signup(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(res));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody @Valid LoginRequest req) {
        LoginResponse res = authService.login(req);
        return ResponseEntity.ok(ApiResponse.of(res));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal String memberUid) {
        authService.logout(memberUid);
        return ResponseEntity.noContent().build();
    }
}
