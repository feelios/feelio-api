package com.korit.feelioapi.domain.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

/**
 * 온보딩 완료 시 전달받는 총자산 정보 (A6-1)
 */
public record OnboardingRequest(
        @NotNull(message = "총자산 금액은 필수입니다.")
        @Min(value = 0, message = "총자산은 0원 이상이어야 합니다.")
        Long totalAsset
) {
}
