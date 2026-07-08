package com.korit.feelioapi.global.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yaml 의 jwt.* 설정 바인딩 (이슈 #1 소관 — 여기서는 읽기만 한다).
 * 키: jwt.issuer / jwt.secret(=${JWT_SECRET}) / jwt.accessTokenTtlSeconds / jwt.refreshTokenTtlSeconds
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String issuer,
        String secret,
        long accessTokenTtlSeconds,
        long refreshTokenTtlSeconds
) {
}
