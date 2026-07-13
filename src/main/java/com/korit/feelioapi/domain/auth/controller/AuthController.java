package com.korit.feelioapi.domain.auth.controller;


import com.korit.feelioapi.domain.auth.dto.LogoutResponse;
import com.korit.feelioapi.domain.auth.dto.TokenRefreshRequest;
import com.korit.feelioapi.domain.auth.dto.TokenRefreshResponse;
import com.korit.feelioapi.domain.auth.service.AuthService;
import com.korit.feelioapi.global.response.ApiResponse;
import com.korit.feelioapi.global.security.AuthCookieManager;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API (API-CONTRACT §3). 최초 로그인은 Spring Security oauth2Login →
 * OAuth2SuccessHandler 가 처리(HttpOnly 쿠키 발급 + 프론트 리다이렉트)하고,
 * 이 컨트롤러는 재발급·로그아웃만 담당한다. 토큰은 HttpOnly 쿠키로만 오간다.
 * Controller 는 얇게 — 비즈니스는 AuthService, 쿠키 발급/삭제는 AuthCookieManager 소관.
 * SecurityConfig 에서 /api/auth/token/refresh 는 permitAll.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookieManager authCookieManager;

    /** POST /api/auth/token/refresh — refreshToken 쿠키로 재발급. 성공 시 200 + 새 토큰 쿠키. */
    @PostMapping("/token/refresh")
    public ApiResponse<TokenRefreshResponse> refreshToken(
            @CookieValue("refreshToken") String refreshToken,
            HttpServletResponse response) {
        TokenRefreshResponse tokens = authService.refreshToken(new TokenRefreshRequest(refreshToken));
        authCookieManager.writeTokens(response, tokens.accessToken(), tokens.refreshToken());
        return ApiResponse.success(tokens);
    }

    /** POST /api/auth/logout — 로그아웃. 인증 필요. 토큰 쿠키를 만료시킨다. */
    @PostMapping("/logout")
    public ApiResponse<LogoutResponse> logout(
            @AuthenticationPrincipal Long userId,
            HttpServletResponse response) {
        LogoutResponse logoutResponse = authService.logout(userId);
        authCookieManager.clearTokens(response);
        return ApiResponse.success(logoutResponse);
    }
}
