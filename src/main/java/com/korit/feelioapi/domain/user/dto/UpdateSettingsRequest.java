package com.korit.feelioapi.domain.user.dto;

/**
 * PATCH /api/users/me/settings 요청 (API-CONTRACT §4). 부분 전송 허용.
 * themeMode: LIGHT | DARK / auroraTheme: theme.js auroras 키.
 * 둘 다 null 이면 변경할 값이 없어 Service 가 VALIDATION_ERROR 를 던진다.
 */
public record UpdateSettingsRequest(
        String themeMode,
        String auroraTheme
) {
}
