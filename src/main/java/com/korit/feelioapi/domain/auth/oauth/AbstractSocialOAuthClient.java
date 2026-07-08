package com.korit.feelioapi.domain.auth.oauth;

import com.korit.feelioapi.global.exception.BusinessException;
import com.korit.feelioapi.global.exception.ErrorCode;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * provider 공통 서버교환 흐름 템플릿.
 * 1) authorization_code + client_secret 으로 token 엔드포인트에서 access_token 교환
 * 2) userinfo 엔드포인트 호출 → provider 별 파싱은 하위 클래스가 담당
 * 엔드포인트·자격증명은 application.yaml 에 이미 등록된 ClientRegistration 을 재사용한다.
 * 교환/조회 실패(HTTP 오류·필드 누락)는 UNAUTHORIZED 로 통일한다(계약 §3: code 만료·재사용·교환 실패 포함).
 */
public abstract class AbstractSocialOAuthClient implements SocialOAuthClient {

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final RestClient restClient;

    protected AbstractSocialOAuthClient(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.restClient = RestClient.create();
    }

    @Override
    public OAuthUserProfile authenticate(String code, String redirectUri, String codeVerifier) {
        ClientRegistration registration =
                clientRegistrationRepository.findByRegistrationId(provider().registrationId());
        if (registration == null) {
            throw new BusinessException(ErrorCode.INVALID_PROVIDER);
        }
        String accessToken = exchangeCodeForAccessToken(registration, code, redirectUri, codeVerifier);
        Map<String, Object> userInfo = fetchUserInfo(registration, accessToken);
        return parseProfile(userInfo);
    }

    /** provider별 userinfo 응답 → 공통 프로필 매핑. */
    protected abstract OAuthUserProfile parseProfile(Map<String, Object> userInfo);

    private String exchangeCodeForAccessToken(ClientRegistration registration,
                                              String code, String redirectUri, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("client_id", registration.getClientId());
        if (registration.getClientSecret() != null) {
            form.add("client_secret", registration.getClientSecret());
        }
        if (codeVerifier != null && !codeVerifier.isBlank()) {
            form.add("code_verifier", codeVerifier);
        }
        try {
            Map<String, Object> token = restClient.post()
                    .uri(registration.getProviderDetails().getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            Object accessToken = token == null ? null : token.get("access_token");
            if (accessToken == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            return accessToken.toString();
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "provider 토큰 교환에 실패했습니다.");
        }
    }

    private Map<String, Object> fetchUserInfo(ClientRegistration registration, String accessToken) {
        try {
            Map<String, Object> userInfo = restClient.get()
                    .uri(registration.getProviderDetails().getUserInfoEndpoint().getUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (userInfo == null || userInfo.isEmpty()) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            return userInfo;
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "provider 프로필 조회에 실패했습니다.");
        }
    }
}
