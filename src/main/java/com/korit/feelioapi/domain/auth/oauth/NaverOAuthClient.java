package com.korit.feelioapi.domain.auth.oauth;

import com.korit.feelioapi.global.exception.BusinessException;
import com.korit.feelioapi.global.exception.ErrorCode;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * Naver 서버교환. userinfo 응답:
 * { "resultcode", "message", "response": { "id", "email", "nickname", "profile_image" } }
 */
@Component
public class NaverOAuthClient extends AbstractSocialOAuthClient {

    public NaverOAuthClient(ClientRegistrationRepository clientRegistrationRepository) {
        super(clientRegistrationRepository);
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.NAVER;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected OAuthUserProfile parseProfile(Map<String, Object> userInfo) {
        Map<String, Object> response = (Map<String, Object>) userInfo.get("response");
        Object id = response == null ? null : response.get("id");
        if (id == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return new OAuthUserProfile(
                Objects.toString(id),
                asString(response.get("email")),
                asString(response.get("nickname")),
                asString(response.get("profile_image"))
        );
    }

    private String asString(Object value) {
        return value == null ? null : Objects.toString(value);
    }
}
