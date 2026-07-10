package com.korit.feelioapi.domain.auth.controller;


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


    /** POST /api/auth/token/refresh — 토큰 재발급. 성공 시 200 + 공통 봉투. */
    @PostMapping("/token/refresh")
    public ApiResponse<TokenRefreshResponse> refreshToken(@org.springframework.web.bind.annotation.CookieValue("refreshToken") String refreshToken, jakarta.servlet.http.HttpServletResponse response) {
        TokenRefreshResponse tokens = authService.refreshToken(new TokenRefreshRequest(refreshToken));
        
        // 새로 발급된 토큰도 쿠키에 구워줍니다. (이 로직은 AuthService나 Handler에서 하는게 좋지만 간략하게 여기서 처리)
        jakarta.servlet.http.Cookie accessCookie = new jakarta.servlet.http.Cookie("accessToken", tokens.accessToken());
        accessCookie.setPath("/");
        accessCookie.setHttpOnly(true);
        accessCookie.setMaxAge(3600); // 1시간
        response.addCookie(accessCookie);
        
        jakarta.servlet.http.Cookie refreshCookie = new jakarta.servlet.http.Cookie("refreshToken", tokens.refreshToken());
        refreshCookie.setPath("/");
        refreshCookie.setHttpOnly(true);
        refreshCookie.setMaxAge(1209600); // 14일
        response.addCookie(refreshCookie);
        
        return ApiResponse.success(tokens);
    }

    /** POST /api/auth/logout — 로그아웃. 인증 필요. */
    @PostMapping("/logout")
    public ApiResponse<LogoutResponse> logout(@AuthenticationPrincipal Long userId, jakarta.servlet.http.HttpServletResponse response) {
        LogoutResponse logoutResponse = authService.logout(userId);
        
        jakarta.servlet.http.Cookie accessCookie = new jakarta.servlet.http.Cookie("accessToken", "");
        accessCookie.setPath("/");
        accessCookie.setMaxAge(0);
        response.addCookie(accessCookie);
        
        jakarta.servlet.http.Cookie refreshCookie = new jakarta.servlet.http.Cookie("refreshToken", "");
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(0);
        response.addCookie(refreshCookie);
        
        return ApiResponse.success(logoutResponse);
    }
}
