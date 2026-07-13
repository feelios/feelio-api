package com.korit.feelioapi.global.security;

import com.korit.feelioapi.global.exception.ErrorCode;
import com.korit.feelioapi.global.security.jwt.JwtProvider;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * accessToken HttpOnly 쿠키를 검증해 성공 시 user_id 를 principal 로 SecurityContext 에 주입한다(BFF).
 * 쿠키가 없으면 그냥 통과(공개 경로 대응), 무효/만료면 에러코드를 request attribute 로 남겨
 * JwtAuthenticationEntryPoint 가 UNAUTHORIZED / TOKEN_EXPIRED 를 구분하도록 한다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ERROR_CODE_ATTRIBUTE = "jwtErrorCode";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = null;
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("accessToken".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }
        
        if (token != null) {
            try {
                Long userId = jwtProvider.parseUserId(token);
                Authentication authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ExpiredJwtException e) {
                request.setAttribute(ERROR_CODE_ATTRIBUTE, ErrorCode.TOKEN_EXPIRED);
            } catch (JwtException | IllegalArgumentException e) {
                request.setAttribute(ERROR_CODE_ATTRIBUTE, ErrorCode.UNAUTHORIZED);
            }
        }
        filterChain.doFilter(request, response);
    }
}
