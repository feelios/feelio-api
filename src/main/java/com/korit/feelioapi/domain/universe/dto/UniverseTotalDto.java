package com.korit.feelioapi.domain.universe.dto;

/**
 * 기준 월의 수입·지출 합계(내부용).
 */
public record UniverseTotalDto(
        Long monthlyIncome,
        Long monthlyExpense
) {
}
