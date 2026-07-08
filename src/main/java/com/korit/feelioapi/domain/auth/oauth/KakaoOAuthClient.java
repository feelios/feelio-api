package com.korit.feelioapi.domain.auth.oauth;

import com.korit.feelioapi.global.exception.BusinessException;
import com.korit.feelioapi.global.exception.ErrorCode;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * Kakao 서버교환. userinfo 응답:
 * { "id": 123, "kakao_account": { "email", "profile": { "nickname", "profile_image_url" } } }
 */
@Component
public class KakaoOAuthClient extends AbstractSocialOAuthClient {

    public KakaoOAuthClient(ClientRegistrationRepository clientRegistrationRepository) {
        super(clientRegistrationRepository);
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected OAuthUserProfile parseProfile(Map<String, Object> userInfo) {
        Object id = userInfo.get("id");
        if (id == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Map<String, Object> account = (Map<String, Object>) userInfo.getOrDefault("kakao_account", Map.of());
        Map<String, Object> profile = (Map<String, Object>) account.getOrDefault("profile", Map.of());
        return new OAuthUserProfile(
                Objects.toString(id),
                asString(account.get("email")),
                asString(profile.get("nickname")),
                asString(profile.get("profile_image_url"))
        );
    }

    private String asString(Object value) {
        return value == null ? null : Objects.toString(value);
    }
}
