package com.korit.feelioapi.domain.auth.oauth;

/**
 * provider 서버교환(A안) 추상화.
 * 프론트가 준 code + redirectUri 로 백엔드가 서버-투-서버 토큰교환 후 프로필을 반환한다.
 * 교환/검증 실패는 BusinessException(UNAUTHORIZED) 로 던진다.
 */
public interface SocialOAuthClient {

    SocialProvider provider();

    OAuthUserProfile authenticate(String code, String redirectUri, String codeVerifier);
}
