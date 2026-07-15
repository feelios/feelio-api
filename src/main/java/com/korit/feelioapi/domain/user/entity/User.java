package com.korit.feelioapi.domain.user.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * users 테이블 행 매핑 (순수 POJO — JPA 아님).
 * users 도메인이 소유한다(§4 조회·수정·온보딩·설정·탈퇴가 모두 이 도메인).
 */
@Getter
@Setter
public class User {

    private Long userId;
    private String nickname;
    private String email;
    private String profileImageUrl;
    private Long totalAsset;
    private Boolean onboardingDone;
    private String themeMode;
    private String auroraTheme;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
