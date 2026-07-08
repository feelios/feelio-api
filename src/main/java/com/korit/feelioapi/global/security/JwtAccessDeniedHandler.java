package com.korit.feelioapi.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.korit.feelioapi.global.exception.ErrorCode;
import com.korit.feelioapi.global.response.ApiError;
import com.korit.feelioapi.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증은 됐으나 권한이 부족한 접근(403) 처리. FORBIDDEN 봉투 JSON 을 직접 쓴다.
 * Security 필터는 @RestControllerAdvice 밖이라 자체 ObjectMapper 로 직렬화한다.
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ErrorCode errorCode = ErrorCode.FORBIDDEN;

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiError error = new ApiError(errorCode.name(), errorCode.getDefaultMessage());
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(error));
    }
}
