package com.korit.feelioapi.domain.analysis.dto;

import java.util.List;

public record MonthlyTrendResponse(
        Long currentTotalAmount,
        Double comparedToLastMonth,
        String trendMessage,
        List<MonthlyData> monthlyData
) {
    public record MonthlyData(
            String label,
            Long amount
    ) {}
}
