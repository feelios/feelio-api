package com.korit.feelioapi.global.security.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    /**
     * 실패 시 돌아갈 프론트 경로.
     *
     * 예전에는 "/login?error=true" 로 보냈으나 nginx 가 /login 을 백엔드로 프록시해서,
     * 로그인 안내 화면 대신 Spring Security 의 401 JSON 이 페이지 전체로 노출됐다(#177).
     * 루트로 보내면 SPA 가 로그인 화면을 그리므로 nginx 설정과 무관하게 안전하다.
     *
     * error 파라미터는 지금 프론트가 읽지 않지만, 나중에 안내 문구를 띄울 때
     * 백엔드를 다시 고치지 않아도 되도록 남겨둔다.
     */
    private static final String FAILURE_PATH = "/?error=login_failed";

    // 프론트엔드 URL. 프로필별로 다르므로 application.yaml 의 client.url 을 따른다.
    @Value("${client.url}")
    private String clientUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        // 표준 로깅으로 남긴다. System.err 은 로그 수집·필터링에서 빠져 원인 추적이 어렵다.
        log.error("OAuth2 로그인 실패: {}", exception.getMessage(), exception);

        getRedirectStrategy().sendRedirect(request, response, clientUrl + FAILURE_PATH);
    }
}
