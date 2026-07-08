package com.korit.feelioapi.domain.user.service;

import com.korit.feelioapi.domain.user.dto.SettingsResponse;
import com.korit.feelioapi.domain.user.dto.UpdateSettingsRequest;
import com.korit.feelioapi.domain.user.dto.UserResponse;
import com.korit.feelioapi.domain.user.entity.User;
import com.korit.feelioapi.domain.user.mapper.UserMapper;
import com.korit.feelioapi.global.exception.BusinessException;
import com.korit.feelioapi.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserService 단위 테스트 (계약 §4). UserMapper 목킹.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserMapper userMapper;

    @InjectMocks private UserService userService;

    private User user(long id, String nickname) {
        User u = new User();
        u.setUserId(id);
        u.setNickname(nickname);
        u.setEmail("user@example.com");
        u.setProfileImageUrl("https://img/p.jpg");
        u.setOnboardingDone(false);
        u.setThemeMode("LIGHT");
        u.setAuroraTheme("블루");
        u.setStatus("ACTIVE");
        return u;
    }

    @Test
    void 내정보_조회시_provider포함_계약필드로_반환한다() {
        when(userMapper.findUserById(1L)).thenReturn(user(1L, "서연"));
        when(userMapper.findProviderByUserId(1L)).thenReturn("GOOGLE");

        UserResponse response = userService.getMe(1L);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.nickname()).isEqualTo("서연");
        assertThat(response.provider()).isEqualTo("GOOGLE");
        assertThat(response.onboardingDone()).isFalse();
        assertThat(response.themeMode()).isEqualTo("LIGHT");
        assertThat(response.auroraTheme()).isEqualTo("블루");
    }

    @Test
    void 존재하지_않는_사용자는_NOT_FOUND() {
        when(userMapper.findUserById(99L)).thenReturn(null);

        assertThatThrownBy(() -> userService.getMe(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);

        verify(userMapper, never()).findProviderByUserId(99L);
    }

    @Test
    void 닉네임_수정시_trim후_저장하고_갱신값을_반환한다() {
        when(userMapper.findUserById(1L)).thenReturn(user(1L, "서연"));
        when(userMapper.findProviderByUserId(1L)).thenReturn("KAKAO");

        UserResponse response = userService.updateNickname(1L, "  하늘  ");

        verify(userMapper).updateNickname(eq(1L), eq("하늘"));
        assertThat(response.nickname()).isEqualTo("하늘");
        assertThat(response.provider()).isEqualTo("KAKAO");
    }

    @Test
    void 수정_대상이_없으면_NOT_FOUND이고_update안한다() {
        when(userMapper.findUserById(99L)).thenReturn(null);

        assertThatThrownBy(() -> userService.updateNickname(99L, "하늘"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);

        verify(userMapper, never()).updateNickname(eq(99L), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 온보딩_완료시_플래그를_켜고_true를_반환한다() {
        when(userMapper.findUserById(1L)).thenReturn(user(1L, "서연"));

        var response = userService.completeOnboarding(1L);

        assertThat(response.onboardingDone()).isTrue();
        verify(userMapper).markOnboardingDone(1L);
    }

    @Test
    void 온보딩_대상이_없으면_NOT_FOUND이고_update안한다() {
        when(userMapper.findUserById(99L)).thenReturn(null);

        assertThatThrownBy(() -> userService.completeOnboarding(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);

        verify(userMapper, never()).markOnboardingDone(99L);
    }

    @Test
    void 설정_부분수정_themeMode만_보내면_해당컬럼만_갱신한다() {
        User updated = user(1L, "서연");
        updated.setThemeMode("DARK");
        when(userMapper.findUserById(1L)).thenReturn(updated);

        SettingsResponse response = userService.updateSettings(1L, new UpdateSettingsRequest("DARK", null));

        assertThat(response.themeMode()).isEqualTo("DARK");
        verify(userMapper).updateSettings(eq(1L), eq("DARK"), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void 설정_부분수정_둘다_보내면_모두_갱신한다() {
        User updated = user(1L, "서연");
        updated.setThemeMode("DARK");
        updated.setAuroraTheme("핑크");
        when(userMapper.findUserById(1L)).thenReturn(updated);

        SettingsResponse response = userService.updateSettings(1L, new UpdateSettingsRequest("DARK", "핑크"));

        assertThat(response.themeMode()).isEqualTo("DARK");
        assertThat(response.auroraTheme()).isEqualTo("핑크");
        verify(userMapper).updateSettings(eq(1L), eq("DARK"), eq("핑크"));
    }

    @Test
    void 설정_둘다_없으면_VALIDATION_ERROR() {
        assertThatThrownBy(() -> userService.updateSettings(1L, new UpdateSettingsRequest(null, "  ")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(userMapper, never()).updateSettings(any(), any(), any());
    }

    @Test
    void 잘못된_themeMode는_VALIDATION_ERROR() {
        assertThatThrownBy(() -> userService.updateSettings(1L, new UpdateSettingsRequest("PURPLE", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(userMapper, never()).updateSettings(any(), any(), any());
    }
}
