package com.korit.feelioapi.domain.auth.dto;

public record TokenRefreshResponse(
        String accessToken,
        String refreshToken
) {
}
