package com.korit.feelioapi.domain.user.dto;

import com.korit.feelioapi.domain.user.entity.User;

/**
 * 사용자 응답 객체 (API-CONTRACT §4 / §3 login 의 user 와 동일 구조).
 * DB 행(User entity)을 그대로 노출하지 않고 계약 필드만 담는다.
 */
public record UserResponse(
        Long userId,
        String nickname,
        String email,
        String profileImageUrl,
        String provider,
        boolean onboardingDone,
        String themeMode,
        String auroraTheme
) {
    public static UserResponse of(User user, String provider) {
        return new UserResponse(
                user.getUserId(),
                user.getNickname(),
                user.getEmail(),
                user.getProfileImageUrl(),
                provider,
                Boolean.TRUE.equals(user.getOnboardingDone()),
                user.getThemeMode(),
                user.getAuroraTheme()
        );
    }
}
