package com.korit.feelioapi.domain.auth.service;

import com.korit.feelioapi.domain.auth.dto.LoginRequest;
import com.korit.feelioapi.domain.auth.dto.LoginResponse;
import com.korit.feelioapi.domain.auth.entity.SocialAccount;
import com.korit.feelioapi.domain.auth.entity.TermsAgreement;
import com.korit.feelioapi.domain.auth.entity.User;
import com.korit.feelioapi.domain.auth.mapper.AuthMapper;
import com.korit.feelioapi.domain.auth.oauth.OAuthUserProfile;
import com.korit.feelioapi.domain.auth.oauth.SocialOAuthClient;
import com.korit.feelioapi.domain.auth.oauth.SocialOAuthClientResolver;
import com.korit.feelioapi.domain.auth.oauth.SocialProvider;
import com.korit.feelioapi.domain.auth.support.TokenHasher;
import com.korit.feelioapi.global.exception.BusinessException;
import com.korit.feelioapi.global.exception.ErrorCode;
import com.korit.feelioapi.global.security.jwt.JwtProperties;
import com.korit.feelioapi.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService 단위 테스트 (계약 §3). provider 서버교환은 SocialOAuthClient 목킹으로 대체.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private SocialOAuthClientResolver oAuthClientResolver;
    @Mock private AuthMapper authMapper;
    @Mock private JwtProvider jwtProvider;
    @Mock private TokenHasher tokenHasher;
    @Mock private SocialOAuthClient googleClient;

    private AuthService authService;

    private final JwtProperties jwtProperties = new JwtProperties("korit", "test-secret", 3600L, 86_400L);

    @BeforeEach
    void setUp() {
        authService = new AuthService(oAuthClientResolver, authMapper, jwtProvider, jwtProperties, tokenHasher);
    }

    private LoginRequest googleRequest() {
        return new LoginRequest("GOOGLE", "auth-code", "https://feelio.app/auth/callback", null);
    }

    private OAuthUserProfile googleProfile() {
        return new OAuthUserProfile("google-sub-1", "user@example.com", "서연", "https://img/p.jpg");
    }

    @Test
    void 신규회원_로그인시_5개테이블_생성후_토큰발급() {
        when(oAuthClientResolver.resolve(SocialProvider.GOOGLE)).thenReturn(googleClient);
        when(googleClient.authenticate("auth-code", "https://feelio.app/auth/callback", null))
                .thenReturn(googleProfile());
        when(authMapper.findSocialAccountByProvider("GOOGLE", "google-sub-1")).thenReturn(null);
        doAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setUserId(10L);
            return null;
        }).when(authMapper).insertUser(any(User.class));
        when(jwtProvider.createAccessToken(10L)).thenReturn("access-token");
        when(jwtProvider.createRefreshToken(10L)).thenReturn("refresh-token");
        when(tokenHasher.hash("refresh-token")).thenReturn("hashed-refresh");

        LoginResponse response = authService.login(googleRequest());

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().userId()).isEqualTo(10L);
        assertThat(response.user().provider()).isEqualTo("GOOGLE");
        assertThat(response.user().nickname()).isEqualTo("서연");
        assertThat(response.user().onboardingDone()).isFalse();
        assertThat(response.user().themeMode()).isEqualTo("LIGHT");
        assertThat(response.user().auroraTheme()).isEqualTo("블루");

        verify(authMapper).insertUser(any(User.class));
        verify(authMapper).insertSocialAccount(any(SocialAccount.class));
        verify(authMapper).insertNotificationSettingDefault(10L);
        verify(authMapper).insertRefreshToken(any());
        verify(authMapper, never()).findUserById(anyLong());

        ArgumentCaptor<List<TermsAgreement>> captor = ArgumentCaptor.captor();
        verify(authMapper).insertTermsAgreements(captor.capture());
        List<TermsAgreement> terms = captor.getValue();
        assertThat(terms).extracting(TermsAgreement::getTermsType)
                .containsExactly("SERVICE", "PRIVACY", "MARKETING");
        assertThat(terms).extracting(TermsAgreement::getAgreed)
                .containsExactly(true, true, false);
        assertThat(terms).allSatisfy(t -> assertThat(t.getVersion()).isEqualTo("1.0"));
    }

    @Test
    void 기존회원_로그인시_가입없이_토큰발급() {
        SocialAccount existing = new SocialAccount();
        existing.setUserId(5L);
        User user = new User();
        user.setUserId(5L);
        user.setNickname("기존");
        user.setOnboardingDone(true);
        user.setThemeMode("DARK");
        user.setAuroraTheme("핑크");

        when(oAuthClientResolver.resolve(SocialProvider.GOOGLE)).thenReturn(googleClient);
        when(googleClient.authenticate(any(), any(), any())).thenReturn(googleProfile());
        when(authMapper.findSocialAccountByProvider("GOOGLE", "google-sub-1")).thenReturn(existing);
        when(authMapper.findUserById(5L)).thenReturn(user);
        when(jwtProvider.createAccessToken(5L)).thenReturn("access-token");
        when(jwtProvider.createRefreshToken(5L)).thenReturn("refresh-token");
        when(tokenHasher.hash("refresh-token")).thenReturn("hashed-refresh");

        LoginResponse response = authService.login(googleRequest());

        assertThat(response.user().userId()).isEqualTo(5L);
        assertThat(response.user().onboardingDone()).isTrue();
        verify(authMapper, never()).insertUser(any());
        verify(authMapper, never()).insertSocialAccount(any());
        verify(authMapper, never()).insertTermsAgreements(any());
        verify(authMapper).insertRefreshToken(any());
    }

    @Test
    void 지원하지않는_provider는_INVALID_PROVIDER() {
        LoginRequest request = new LoginRequest("FACEBOOK", "code", "uri", null);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PROVIDER);

        verify(authMapper, never()).insertRefreshToken(any());
    }

    @Test
    void provider_교환실패시_UNAUTHORIZED_전파() {
        when(oAuthClientResolver.resolve(SocialProvider.GOOGLE)).thenReturn(googleClient);
        when(googleClient.authenticate(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        assertThatThrownBy(() -> authService.login(googleRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(authMapper, never()).insertUser(any());
        verify(authMapper, never()).insertRefreshToken(any());
    }
}
