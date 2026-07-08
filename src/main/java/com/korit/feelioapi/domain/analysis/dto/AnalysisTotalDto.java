package com.korit.feelioapi.domain.analysis.dto;

/**
 * 월 수입·지출 합계 매퍼 결과.
 */
public record AnalysisTotalDto(
        Long totalIncome,
        Long totalExpense
) {
}
