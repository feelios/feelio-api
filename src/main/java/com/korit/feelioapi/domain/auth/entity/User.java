package com.korit.feelioapi.domain.auth.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * users 테이블 행 매핑 (순수 POJO — JPA 아님).
 * 신규 가입 시 nickname/email/profileImageUrl 만 채워 insert 하고
 * 나머지(onboardingDone/themeMode/auroraTheme/status/타임스탬프)는 DB 기본값을 따른다.
 */
@Getter
@Setter
public class User {

    private Long userId;
    private String nickname;
    private String email;
    private String profileImageUrl;
    private Boolean onboardingDone;
    private String themeMode;
    private String auroraTheme;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
