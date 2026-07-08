package com.korit.feelioapi.domain.user.service;

import com.korit.feelioapi.domain.user.dto.OnboardingResponse;
import com.korit.feelioapi.domain.user.dto.SettingsResponse;
import com.korit.feelioapi.domain.user.dto.UpdateSettingsRequest;
import com.korit.feelioapi.domain.user.dto.UserResponse;
import com.korit.feelioapi.domain.user.entity.User;

import java.util.Set;
import com.korit.feelioapi.domain.user.mapper.UserMapper;
import com.korit.feelioapi.global.exception.BusinessException;
import com.korit.feelioapi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 조회·수정 (API-CONTRACT §4). 항상 인증 주체 user_id 기준으로만 접근한다.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserResponse getMe(Long userId) {
        return toResponse(loadUser(userId));
    }

    @Transactional
    public UserResponse updateNickname(Long userId, String nickname) {
        User user = loadUser(userId);
        String trimmed = nickname.trim();
        userMapper.updateNickname(userId, trimmed);
        user.setNickname(trimmed);
        return toResponse(user);
    }

    @Transactional
    public OnboardingResponse completeOnboarding(Long userId) {
        loadUser(userId); // 본인 존재 확인(없으면 NOT_FOUND)
        userMapper.markOnboardingDone(userId);
        return new OnboardingResponse(true);
    }

    private static final Set<String> THEME_MODES = Set.of("LIGHT", "DARK");

    @Transactional
    public SettingsResponse updateSettings(Long userId, UpdateSettingsRequest request) {
        boolean hasTheme = request.themeMode() != null;
        boolean hasAurora = request.auroraTheme() != null && !request.auroraTheme().isBlank();
        if (!hasTheme && !hasAurora) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "변경할 설정이 없습니다.");
        }
        if (hasTheme && !THEME_MODES.contains(request.themeMode())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "themeMode 는 LIGHT 또는 DARK 여야 합니다.");
        }

        loadUser(userId); // 본인 존재 확인(없으면 NOT_FOUND)
        userMapper.updateSettings(userId, hasTheme ? request.themeMode() : null,
                hasAurora ? request.auroraTheme() : null);
        return SettingsResponse.of(loadUser(userId));
    }

    private User loadUser(Long userId) {
        User user = userMapper.findUserById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return user;
    }

    /** provider 는 연동된 소셜 계정에서 최신 1건을 채운다(§3 user 객체와 동일 구조). */
    private UserResponse toResponse(User user) {
        String provider = userMapper.findProviderByUserId(user.getUserId());
        return UserResponse.of(user, provider);
    }
}
