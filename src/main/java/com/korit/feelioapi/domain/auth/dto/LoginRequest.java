package com.korit.feelioapi.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 소셜 로그인 요청 (API-CONTRACT §3).
 * - provider: GOOGLE | KAKAO | NAVER (값 검증은 SocialProvider.from 에서 INVALID_PROVIDER 로 처리)
 * - code: provider 인가 서버가 redirect 로 돌려준 1회용 authorization code
 * - redirectUri: authorize 요청 때 쓴 값과 정확히 동일해야 함
 * - codeVerifier: PKCE 적용 시(선택)
 */
public record LoginRequest(
        @NotBlank(message = "provider는 필수입니다.") String provider,
        @NotBlank(message = "code는 필수입니다.") String code,
        @NotBlank(message = "redirectUri는 필수입니다.") String redirectUri,
        String codeVerifier
) {
}
