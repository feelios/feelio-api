package com.korit.feelioapi.domain.auth.oauth;

import com.korit.feelioapi.global.exception.BusinessException;
import com.korit.feelioapi.global.exception.ErrorCode;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * Google 서버교환. userinfo 응답: { "sub", "email", "name", "picture" }.
 */
@Component
public class GoogleOAuthClient extends AbstractSocialOAuthClient {

    public GoogleOAuthClient(ClientRegistrationRepository clientRegistrationRepository) {
        super(clientRegistrationRepository);
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.GOOGLE;
    }

    @Override
    protected OAuthUserProfile parseProfile(Map<String, Object> userInfo) {
        Object sub = userInfo.get("sub");
        if (sub == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return new OAuthUserProfile(
                sub.toString(),
                asString(userInfo.get("email")),
                asString(userInfo.get("name")),
                asString(userInfo.get("picture"))
        );
    }

    private String asString(Object value) {
        return value == null ? null : Objects.toString(value);
    }
}
