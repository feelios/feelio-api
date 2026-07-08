package com.korit.feelioapi.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 발급/검증 유틸 (jjwt 0.12/0.13 비-deprecated API).
 * - subject 에 user_id, claim "type" 으로 access/refresh 구분
 * - 만료 토큰은 parseSignedClaims 가 ExpiredJwtException 을 던진다(필터가 구분 처리)
 */
@Component
public class JwtProvider {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final String issuer;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;

    public JwtProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.issuer();
        this.accessTokenTtlSeconds = properties.accessTokenTtlSeconds();
        this.refreshTokenTtlSeconds = properties.refreshTokenTtlSeconds();
    }

    public String createAccessToken(Long userId) {
        return create(userId, TYPE_ACCESS, accessTokenTtlSeconds);
    }

    public String createRefreshToken(Long userId) {
        return create(userId, TYPE_REFRESH, refreshTokenTtlSeconds);
    }

    /** 서명·발급자·만료를 검증하고 subject(user_id)를 반환한다. 실패 시 JwtException 계열을 던진다. */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.valueOf(claims.getSubject());
    }

    private String create(Long userId, String type, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }
}
