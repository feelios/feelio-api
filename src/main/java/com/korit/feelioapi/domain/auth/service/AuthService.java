package com.korit.feelioapi.domain.auth.service;

import com.korit.feelioapi.domain.auth.dto.LoginRequest;
import com.korit.feelioapi.domain.auth.dto.LoginResponse;
import com.korit.feelioapi.domain.auth.dto.TokenRefreshRequest;
import com.korit.feelioapi.domain.auth.dto.TokenRefreshResponse;
import com.korit.feelioapi.domain.auth.dto.UserResponse;
import com.korit.feelioapi.domain.auth.entity.RefreshToken;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 소셜 로그인(API-CONTRACT §3, A안).
 * code+redirectUri 서버교환 → 프로필 → (provider, provider_user_id) 조회/신규가입 → JWT 발급.
 * 신규가입은 users + social_accounts + notification_settings + terms_agreements 를 한 트랜잭션으로 처리한다.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TERMS_VERSION = "1.0";
    private static final String DEFAULT_NICKNAME = "사용자";

    private final SocialOAuthClientResolver oAuthClientResolver;
    private final AuthMapper authMapper;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final TokenHasher tokenHasher;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        SocialProvider provider = SocialProvider.from(request.provider());
        SocialOAuthClient client = oAuthClientResolver.resolve(provider);

        OAuthUserProfile profile =
                client.authenticate(request.code(), request.redirectUri(), request.codeVerifier());

        SocialAccount social = authMapper.findSocialAccountByProvider(provider.name(), profile.providerUserId());
        User user = (social == null)
                ? signup(provider, profile)
                : loadExistingUser(social.getUserId());

        String accessToken = jwtProvider.createAccessToken(user.getUserId());
        String refreshToken = jwtProvider.createRefreshToken(user.getUserId());
        storeRefreshToken(user.getUserId(), refreshToken);

        return new LoginResponse(accessToken, refreshToken, UserResponse.of(user, provider.name()));
    }

    @Transactional
    public TokenRefreshResponse refreshToken(TokenRefreshRequest request) {
        try {
            Long userId = jwtProvider.parseUserId(request.refreshToken());
            String hash = tokenHasher.hash(request.refreshToken());

            RefreshToken storedToken = authMapper.findRefreshTokenByHash(userId, hash);
            if (storedToken == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
                authMapper.deleteRefreshToken(storedToken.getTokenId());
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }

            authMapper.deleteRefreshToken(storedToken.getTokenId());

            String newAccessToken = jwtProvider.createAccessToken(userId);
            String newRefreshToken = jwtProvider.createRefreshToken(userId);
            storeRefreshToken(userId, newRefreshToken);

            return new TokenRefreshResponse(newAccessToken, newRefreshToken);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    /** 신규 가입: 5개 테이블 원자적 생성. */
    private User signup(SocialProvider provider, OAuthUserProfile profile) {
        User user = new User();
        user.setNickname(resolveNickname(profile));
        user.setEmail(profile.email());
        user.setProfileImageUrl(profile.profileImageUrl());
        authMapper.insertUser(user); // useGeneratedKeys → userId 채움
        // 응답용 기본값 반영(DB DEFAULT 와 동일). 재조회를 아끼기 위해 메모리에서 채운다.
        user.setOnboardingDone(false);
        user.setThemeMode("LIGHT");
        user.setAuroraTheme("블루");
        user.setStatus("ACTIVE");

        SocialAccount social = new SocialAccount();
        social.setUserId(user.getUserId());
        social.setProvider(provider.name());
        social.setProviderUserId(profile.providerUserId());
        authMapper.insertSocialAccount(social);

        authMapper.insertNotificationSettingDefault(user.getUserId());
        authMapper.insertTermsAgreements(defaultTerms(user.getUserId()));

        return user;
    }

    private User loadExistingUser(Long userId) {
        User user = authMapper.findUserById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    private void storeRefreshToken(Long userId, String refreshToken) {
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(tokenHasher.hash(refreshToken));
        token.setExpiresAt(LocalDateTime.now().plusSeconds(jwtProperties.refreshTokenTtlSeconds()));
        authMapper.insertRefreshToken(token);
    }

    /** provider 닉네임이 없으면 이메일 로컬파트, 그것도 없으면 기본값으로 대체(users.nickname NOT NULL). */
    private String resolveNickname(OAuthUserProfile profile) {
        if (profile.nickname() != null && !profile.nickname().isBlank()) {
            return profile.nickname();
        }
        String email = profile.email();
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        }
        return DEFAULT_NICKNAME;
    }

    private List<TermsAgreement> defaultTerms(Long userId) {
        return List.of(
                terms(userId, "SERVICE", true),
                terms(userId, "PRIVACY", true),
                terms(userId, "MARKETING", false)
        );
    }

    private TermsAgreement terms(Long userId, String type, boolean agreed) {
        TermsAgreement agreement = new TermsAgreement();
        agreement.setUserId(userId);
        agreement.setTermsType(type);
        agreement.setAgreed(agreed);
        agreement.setVersion(TERMS_VERSION);
        return agreement;
    }
}
