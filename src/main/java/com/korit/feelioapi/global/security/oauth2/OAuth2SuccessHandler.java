package com.korit.feelioapi.global.security.oauth2;

import com.korit.feelioapi.domain.auth.oauth.CustomOAuth2User;
import com.korit.feelioapi.domain.auth.service.AuthService;
import com.korit.feelioapi.global.security.jwt.JwtProperties;
import com.korit.feelioapi.global.security.jwt.JwtProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final AuthService authService;

    // 프론트엔드 URL. 실제 운영 환경에서는 프로퍼티로 빼는 것이 좋습니다.
    private static final String REDIRECT_URI = "http://localhost:5173/";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        Long userId = oAuth2User.getUserId();

        String accessToken = jwtProvider.createAccessToken(userId);
        String refreshToken = jwtProvider.createRefreshToken(userId);

        authService.storeRefreshToken(userId, refreshToken);

        addCookie(response, "accessToken", accessToken, jwtProperties.accessTokenTtlSeconds());
        addCookie(response, "refreshToken", refreshToken, jwtProperties.refreshTokenTtlSeconds());

        getRedirectStrategy().sendRedirect(request, response, REDIRECT_URI);
    }

    private void addCookie(HttpServletResponse response, String name, String value, long maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        // cookie.setSecure(true); // HTTPS 환경에서만 전송. 로컬 테스트를 위해 임시 주석 처리 가능
        cookie.setMaxAge((int) maxAge);
        response.addCookie(cookie);
    }
}
