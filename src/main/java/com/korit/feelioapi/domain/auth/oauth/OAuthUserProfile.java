package com.korit.feelioapi.domain.auth.oauth;

/**
 * provider 로부터 서버교환 후 수신한 사용자 프로필(공통 형태).
 * providerUserId 는 provider 내 고유 식별자(구글 sub / 카카오 id / 네이버 response.id).
 */
public record OAuthUserProfile(
        String providerUserId,
        String email,
        String nickname,
        String profileImageUrl
) {
}
