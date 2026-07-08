package com.korit.feelioapi.domain.auth.support;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * refresh 토큰 저장용 해시 유틸.
 * 원문 refresh JWT 대신 SHA-256 hex(64자)를 refresh_tokens.token_hash 에 저장한다.
 * (global 은 수정 금지라 auth 도메인 내부에 둔다.)
 */
@Component
public class TokenHasher {

    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 지원하지 않음", e);
        }
    }
}
