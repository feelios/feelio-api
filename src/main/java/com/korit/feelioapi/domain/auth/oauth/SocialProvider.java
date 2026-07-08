package com.korit.feelioapi.domain.auth.oauth;

import com.korit.feelioapi.global.exception.BusinessException;
import com.korit.feelioapi.global.exception.ErrorCode;

/**
 * 지원 소셜 제공자 (API-CONTRACT §3). name() 을 DB provider 컬럼 값으로 사용한다.
 * registrationId() 는 application.yaml 의 spring.security.oauth2 등록 id 와 일치.
 */
public enum SocialProvider {

    GOOGLE, KAKAO, NAVER;

    public String registrationId() {
        return name().toLowerCase();
    }

    /** 미지원/누락 provider 는 INVALID_PROVIDER 로 매핑한다. */
    public static SocialProvider from(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PROVIDER);
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_PROVIDER);
        }
    }
}
