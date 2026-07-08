package com.korit.feelioapi.domain.auth.dto;

/**
 * 소셜 로그인 응답 data (API-CONTRACT §3).
 * provider access token 은 절대 포함하지 않는다(백엔드 서버교환 후 폐기).
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        UserResponse user
) {
}
