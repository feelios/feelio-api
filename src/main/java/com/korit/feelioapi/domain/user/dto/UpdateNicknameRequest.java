package com.korit.feelioapi.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/users/me 요청 (API-CONTRACT §4). 닉네임 1~8자.
 * 위반 시 GlobalExceptionHandler 가 VALIDATION_ERROR(400) 로 변환한다.
 */
public record UpdateNicknameRequest(
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 1, max = 8, message = "닉네임은 1~8자여야 합니다.")
        String nickname
) {
}
