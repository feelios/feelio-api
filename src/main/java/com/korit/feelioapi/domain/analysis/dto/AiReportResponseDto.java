package com.korit.feelioapi.domain.analysis.dto;

/** AI 연동 전 분석 리포트 계약. ai 영역은 현재 명시적인 준비 중 문구다. */
public record AiReportResponseDto(
        int year,
        int month,
        long totalExpense,
        long totalBudget,
        double budgetUsageRate,
        String consumptionRisk,
        AiContent ai
) {
    public record AiContent(
            String fact,
            String challenge,
            String emotion
    ) {
    }
}
