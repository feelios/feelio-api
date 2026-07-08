package com.korit.feelioapi.domain.auth.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * refresh_tokens 테이블 행 매핑 (순수 POJO).
 * 원문 refresh JWT 는 저장하지 않고 SHA-256 해시(token_hash)만 보관한다.
 */
@Getter
@Setter
public class RefreshToken {

    private Long tokenId;
    private Long userId;
    private String tokenHash;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
