package com.korit.feelioapi.global.security.oauth2;

import com.korit.feelioapi.domain.auth.oauth.CustomOAuth2User;
import com.korit.feelioapi.domain.auth.service.AuthService;
import com.korit.feelioapi.global.security.AuthCookieManager;
import com.korit.feelioapi.global.security.jwt.JwtProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final AuthService authService;
    private final AuthCookieManager authCookieManager;

    // 프론트엔드 URL. 프로필별로 다르므로 application.yaml 의 client.url 을 따른다.
    @Value("${client.url}")
    private String clientUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        Long userId = oAuth2User.getUserId();

        String accessToken = jwtProvider.createAccessToken(userId);
        String refreshToken = jwtProvider.createRefreshToken(userId);

        authService.storeRefreshToken(userId, refreshToken);

        // 쿠키 속성(HttpOnly·Secure·SameSite·TTL)은 AuthCookieManager 를 단일 기준으로 삼는다.
        // 재발급(AuthController)과 동일한 속성으로 구워야 브라우저가 같은 쿠키로 인식한다.
        authCookieManager.writeTokens(response, accessToken, refreshToken);

        getRedirectStrategy().sendRedirect(request, response, clientUrl);
    }
}
