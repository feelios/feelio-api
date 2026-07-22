package com.korit.feelioapi.global.security.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final String REDIRECT_URI = "http://localhost:5173/login?error=true";

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        // 에러 로그 출력
        System.err.println("OAuth2 Login Failed: " + exception.getMessage());
        exception.printStackTrace();
        
        getRedirectStrategy().sendRedirect(request, response, REDIRECT_URI);
    }
}
