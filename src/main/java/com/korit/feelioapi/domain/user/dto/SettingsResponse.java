package com.korit.feelioapi.domain.user.dto;

import com.korit.feelioapi.domain.user.entity.User;

/**
 * PATCH /api/users/me/settings 응답 data (API-CONTRACT §4) — 갱신된 설정.
 */
public record SettingsResponse(
        String themeMode,
        String auroraTheme
) {
    public static SettingsResponse of(User user) {
        return new SettingsResponse(user.getThemeMode(), user.getAuroraTheme());
    }
}
