package com.korit.feelioapi.domain.auth.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * social_accounts 테이블 행 매핑 (순수 POJO).
 * (provider, provider_user_id) 유니크로 사용자를 식별한다. provider 토큰은 저장하지 않는다.
 */
@Getter
@Setter
public class SocialAccount {

    private Long socialAccountId;
    private Long userId;
    private String provider;
    private String providerUserId;
    private LocalDateTime connectedAt;
}
