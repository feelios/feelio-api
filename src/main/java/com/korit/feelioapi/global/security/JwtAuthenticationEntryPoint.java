package com.korit.feelioapi.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.korit.feelioapi.global.exception.ErrorCode;
import com.korit.feelioapi.global.response.ApiError;
import com.korit.feelioapi.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증되지 않은 접근(401) 처리. Security 필터는 @RestControllerAdvice 밖이라 봉투 JSON 을 직접 쓴다.
 * 필터가 남긴 에러코드로 UNAUTHORIZED / TOKEN_EXPIRED 를 구분한다(없으면 UNAUTHORIZED).
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode errorCode = request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE) instanceof ErrorCode ec
                ? ec
                : ErrorCode.UNAUTHORIZED;

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiError error = new ApiError(errorCode.name(), errorCode.getDefaultMessage());
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(error));
    }
}
