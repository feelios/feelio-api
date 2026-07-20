package com.korit.feelioapi.domain.goal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

/**
 * 목표 생성·수정 공용 요청 (API-CONTRACT §7). POST/PUT 동일 필드.
 * name·targetAmount(>0) 필수. initialAmount는 초기 모은 돈 설정용.
 * isMain=true 면 서버가 같은 트랜잭션에서 기존 대표 목표를 해제한다.
 */
public record GoalRequest(
        @NotBlank(message = "목표 이름은 필수입니다.")
        String name,

        @NotNull(message = "목표 금액은 필수입니다.")
        @Positive(message = "목표 금액은 1원 이상이어야 합니다.")
        Integer targetAmount,

        @PositiveOrZero(message = "초기 모은 돈은 0원 이상이어야 합니다.")
        Long initialAmount,

        LocalDate startDate,
        LocalDate dueDate,
        Boolean isMain
) {
    /** null 은 대표 아님으로 취급. */
    public boolean mainFlag() {
        return Boolean.TRUE.equals(isMain);
    }
}
