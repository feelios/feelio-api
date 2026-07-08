package com.korit.feelioapi.domain.auth.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * terms_agreements 테이블 행 매핑 (순수 POJO). 약관별 1행.
 * 신규 가입 시 SERVICE·PRIVACY(동의) + MARKETING(미동의)을 기록한다.
 */
@Getter
@Setter
public class TermsAgreement {

    private Long agreementId;
    private Long userId;
    private String termsType;
    private Boolean agreed;
    private String version;
    private LocalDateTime agreedAt;
}
