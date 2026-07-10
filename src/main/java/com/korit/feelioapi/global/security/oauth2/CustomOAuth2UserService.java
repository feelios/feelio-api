package com.korit.feelioapi.global.security.oauth2;

import com.korit.feelioapi.domain.auth.entity.User;
import com.korit.feelioapi.domain.auth.oauth.CustomOAuth2User;
import com.korit.feelioapi.domain.auth.oauth.OAuthUserProfile;
import com.korit.feelioapi.domain.auth.oauth.SocialProvider;
import com.korit.feelioapi.domain.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final AuthService authService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        SocialProvider provider = SocialProvider.from(registrationId);

        OAuthUserProfile profile = extractProfile(provider, oAuth2User.getAttributes());

        User user = authService.processSocialUser(provider, profile);

        return new CustomOAuth2User(user.getUserId(), profile.nickname(), oAuth2User.getAttributes());
    }

    private OAuthUserProfile extractProfile(SocialProvider provider, Map<String, Object> attributes) {
        if (provider == SocialProvider.GOOGLE) {
            return new OAuthUserProfile(
                    asString(attributes.get("sub")),
                    asString(attributes.get("email")),
                    asString(attributes.get("name")),
                    asString(attributes.get("picture"))
            );
        } else if (provider == SocialProvider.KAKAO) {
            Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
            Map<String, Object> profile = kakaoAccount != null ? (Map<String, Object>) kakaoAccount.get("profile") : null;
            return new OAuthUserProfile(
                    asString(attributes.get("id")),
                    kakaoAccount != null ? asString(kakaoAccount.get("email")) : null,
                    profile != null ? asString(profile.get("nickname")) : null,
                    profile != null ? asString(profile.get("profile_image_url")) : null
            );
        } else if (provider == SocialProvider.NAVER) {
            Map<String, Object> response = (Map<String, Object>) attributes.get("response");
            if (response == null) {
                response = attributes;
            }
            return new OAuthUserProfile(
                    asString(response.get("id")),
                    asString(response.get("email")),
                    asString(response.get("name")),
                    asString(response.get("profile_image"))
            );
        }
        throw new IllegalArgumentException("지원하지 않는 소셜 로그인입니다: " + provider);
    }

    private String asString(Object value) {
        return value == null ? null : Objects.toString(value);
    }
}
