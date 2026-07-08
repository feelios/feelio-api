package com.korit.feelioapi.domain.auth.controller;

import com.korit.feelioapi.domain.auth.dto.LoginRequest;
import com.korit.feelioapi.domain.auth.dto.LoginResponse;
import com.korit.feelioapi.domain.auth.dto.LogoutResponse;
import com.korit.feelioapi.domain.auth.dto.TokenRefreshRequest;
import com.korit.feelioapi.domain.auth.dto.TokenRefreshResponse;
import com.korit.feelioapi.domain.auth.service.AuthService;
import com.korit.feelioapi.global.response.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API (API-CONTRACT §3). SecurityConfig 에서 /api/auth/** 는 permitAll.
 * Controller 는 얇게 — 검증/트랜잭션/비즈니스는 AuthService 소관.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** POST /api/auth/login — 소셜 로그인(code 서버교환). 성공 시 200 + 공통 봉투. */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    /** POST /api/auth/token/refresh — 토큰 재발급. 성공 시 200 + 공통 봉투. */
    @PostMapping("/token/refresh")
    public ApiResponse<TokenRefreshResponse> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        return ApiResponse.success(authService.refreshToken(request));
    }

    /** POST /api/auth/logout — 로그아웃. 인증 필요. */
    @PostMapping("/logout")
    public ApiResponse<LogoutResponse> logout(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(authService.logout(userId));
    }
}
