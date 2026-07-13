package com.korit.feelioapi.domain.auth.service;

import com.korit.feelioapi.domain.auth.dto.LogoutResponse;
import com.korit.feelioapi.domain.auth.dto.TokenRefreshRequest;
import com.korit.feelioapi.domain.auth.dto.TokenRefreshResponse;
import com.korit.feelioapi.domain.auth.entity.RefreshToken;
import com.korit.feelioapi.domain.auth.entity.SocialAccount;
import com.korit.feelioapi.domain.auth.entity.TermsAgreement;
import com.korit.feelioapi.domain.auth.entity.User;
import com.korit.feelioapi.domain.auth.mapper.AuthMapper;
import com.korit.feelioapi.domain.auth.oauth.OAuthUserProfile;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService 단위 테스트 (계약 §3, BFF 패턴).
 * provider 서버교환·검증은 Spring Security oauth2Login 소관이므로 여기서 다루지 않는다.
 * AuthService 는 프로필 수신 이후의 조회/가입(processSocialUser)·재발급·로그아웃·refresh 저장만 책임진다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthMapper authMapper;
    @Mock private JwtProvider jwtProvider;
    @Mock private TokenHasher tokenHasher;

    private AuthService authService;

    private final JwtProperties jwtProperties = new JwtProperties("korit", "test-secret", 3600L, 1_209_600L);

    @BeforeEach
    void setUp() {
        authService = new AuthService(authMapper, jwtProvider, jwtProperties, tokenHasher);
    }

    private OAuthUserProfile googleProfile() {
        return new OAuthUserProfile("google-sub-1", "user@example.com", "서연", "https://img/p.jpg");
    }

    @Test
    void 신규회원_로그인시_5개테이블_생성후_유저반환() {
        when(authMapper.findSocialAccountByProvider("GOOGLE", "google-sub-1")).thenReturn(null);
        doAnswerSetUserId(10L);

        User user = authService.processSocialUser(SocialProvider.GOOGLE, googleProfile());

        assertThat(user.getUserId()).isEqualTo(10L);
        assertThat(user.getNickname()).isEqualTo("서연");
        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getOnboardingDone()).isFalse();
        assertThat(user.getThemeMode()).isEqualTo("LIGHT");
        assertThat(user.getAuroraTheme()).isEqualTo("블루");

        verify(authMapper).insertUser(any(User.class));
        verify(authMapper).insertSocialAccount(any(SocialAccount.class));
        verify(authMapper).insertNotificationSettingDefault(10L);
        verify(authMapper, never()).findUserById(anyLong());
        // BFF: 토큰/refresh 저장은 OAuth2SuccessHandler 소관이므로 가입 단계에서 발급하지 않는다.
        verify(authMapper, never()).insertRefreshToken(any());

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
    void 기존회원_로그인시_가입없이_유저조회() {
        SocialAccount existing = new SocialAccount();
        existing.setUserId(5L);
        User user = new User();
        user.setUserId(5L);
        user.setNickname("기존");
        user.setOnboardingDone(true);

        when(authMapper.findSocialAccountByProvider("GOOGLE", "google-sub-1")).thenReturn(existing);
        when(authMapper.findUserById(5L)).thenReturn(user);

        User result = authService.processSocialUser(SocialProvider.GOOGLE, googleProfile());

        assertThat(result.getUserId()).isEqualTo(5L);
        assertThat(result.getOnboardingDone()).isTrue();
        verify(authMapper, never()).insertUser(any());
        verify(authMapper, never()).insertSocialAccount(any());
        verify(authMapper, never()).insertTermsAgreements(any());
    }

    @Test
    void 닉네임_없는_프로필은_이메일_로컬파트로_대체() {
        OAuthUserProfile noNickname = new OAuthUserProfile("google-sub-2", "hong@example.com", null, null);
        when(authMapper.findSocialAccountByProvider("GOOGLE", "google-sub-2")).thenReturn(null);
        doAnswerSetUserId(11L);

        User user = authService.processSocialUser(SocialProvider.GOOGLE, noNickname);

        assertThat(user.getNickname()).isEqualTo("hong");
    }

    @Test
    void storeRefreshToken_은_해시해서_TTL과_함께_저장한다() {
        when(tokenHasher.hash("refresh-token")).thenReturn("hashed-refresh");

        authService.storeRefreshToken(7L, "refresh-token");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.captor();
        verify(authMapper).insertRefreshToken(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getTokenHash()).isEqualTo("hashed-refresh");
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void 유효한_리프레시_토큰으로_재발급_성공() {
        TokenRefreshRequest request = new TokenRefreshRequest("valid-refresh-token");
        RefreshToken storedToken = new RefreshToken();
        storedToken.setTokenId(100L);
        storedToken.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(jwtProvider.parseUserId("valid-refresh-token")).thenReturn(5L);
        when(tokenHasher.hash("valid-refresh-token")).thenReturn("hashed-refresh");
        when(authMapper.findRefreshTokenByHash(5L, "hashed-refresh")).thenReturn(storedToken);
        when(jwtProvider.createAccessToken(5L)).thenReturn("new-access");
        when(jwtProvider.createRefreshToken(5L)).thenReturn("new-refresh");

        TokenRefreshResponse response = authService.refreshToken(request);

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        verify(authMapper).deleteRefreshToken(100L);
        verify(authMapper).insertRefreshToken(any(RefreshToken.class));
    }

    @Test
    void 만료된_JWT토큰으로_재발급요청시_UNAUTHORIZED() {
        TokenRefreshRequest request = new TokenRefreshRequest("expired-jwt");
        when(jwtProvider.parseUserId("expired-jwt")).thenThrow(new RuntimeException("Expired JWT"));

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void DB에_없는_리프레시토큰_UNAUTHORIZED() {
        TokenRefreshRequest request = new TokenRefreshRequest("valid-jwt-not-in-db");
        when(jwtProvider.parseUserId("valid-jwt-not-in-db")).thenReturn(5L);
        when(tokenHasher.hash("valid-jwt-not-in-db")).thenReturn("hashed-not-found");
        when(authMapper.findRefreshTokenByHash(5L, "hashed-not-found")).thenReturn(null);

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void DB토큰이_만료기간을_지났으면_삭제후_UNAUTHORIZED() {
        TokenRefreshRequest request = new TokenRefreshRequest("valid-jwt-but-db-expired");
        RefreshToken storedToken = new RefreshToken();
        storedToken.setTokenId(100L);
        storedToken.setExpiresAt(LocalDateTime.now().minusDays(1));

        when(jwtProvider.parseUserId("valid-jwt-but-db-expired")).thenReturn(5L);
        when(tokenHasher.hash("valid-jwt-but-db-expired")).thenReturn("hashed-expired");
        when(authMapper.findRefreshTokenByHash(5L, "hashed-expired")).thenReturn(storedToken);

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(authMapper).deleteRefreshToken(100L);
    }

    @Test
    void 정상_로그아웃시_유저의_모든_토큰을_삭제한다() {
        LogoutResponse response = authService.logout(10L);

        assertThat(response.loggedOut()).isTrue();
        verify(authMapper).deleteAllRefreshTokensByUserId(10L);
    }

    /** insertUser 가 useGeneratedKeys 로 userId 를 채우는 동작을 목킹한다. */
    private void doAnswerSetUserId(long userId) {
        org.mockito.Mockito.doAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setUserId(userId);
            return null;
        }).when(authMapper).insertUser(any(User.class));
    }
}
