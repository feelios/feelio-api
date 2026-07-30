package com.korit.feelioapi.global.security;

import com.korit.feelioapi.global.security.jwt.JwtProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 인증 토큰(accessToken·refreshToken)을 HttpOnly 쿠키로 굽고/지우는 공통 유틸.
 * BFF 패턴: 토큰은 브라우저 JS 에 노출하지 않고 HttpOnly 쿠키로만 오간다.
 * 최초 로그인(OAuth2SuccessHandler)·재발급/로그아웃(AuthController)이 동일한 쿠키 속성으로
 * 발급/삭제하도록 발급 로직을 한곳에 모은다. TTL 은 JwtProperties(jwt.*) 를 단일 기준으로 삼는다.
 */
@Component
@RequiredArgsConstructor
public class AuthCookieManager {

    public static final String ACCESS_TOKEN_COOKIE = "accessToken";
    public static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    private final JwtProperties jwtProperties;

    /** 운영(https)은 true, 로컬(http)은 false. application.yaml 의 프로필별 cookie.secure 를 따른다. */
    @Value("${cookie.secure}")
    private boolean secure;

    /** 로그인·재발급 성공 시 두 토큰을 각자의 TTL 로 HttpOnly 쿠키에 굽는다. */
    public void writeTokens(HttpServletResponse response, String accessToken, String refreshToken) {
        addCookie(response, ACCESS_TOKEN_COOKIE, accessToken, jwtProperties.accessTokenTtlSeconds());
        addCookie(response, REFRESH_TOKEN_COOKIE, refreshToken, jwtProperties.refreshTokenTtlSeconds());
    }

    /** 로그아웃 시 두 토큰 쿠키를 즉시 만료시킨다(maxAge=0 → 브라우저가 삭제). */
    public void clearTokens(HttpServletResponse response) {
        addCookie(response, ACCESS_TOKEN_COOKIE, "", 0);
        addCookie(response, REFRESH_TOKEN_COOKIE, "", 0);
    }

    private void addCookie(HttpServletResponse response, String name, String value, long maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .path("/")
                .httpOnly(true)
                .secure(secure)
                .maxAge(maxAge)
                .sameSite("Lax") // OAuth 리다이렉트(top-level navigation)로 쿠키가 실려야 한다
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
