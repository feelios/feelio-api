package com.korit.feelioapi.domain.analysis.dto;

import java.util.List;

public record MonthlyTrendResponse(
        Long currentTotalAmount,
        Double comparedToLastMonth,
        String trendMessage,
        List<MonthlyData> monthlyData
) {
    /**
     * @param yearMonth "2026-07". 라벨("7월")만으로는 연도를 알 수 없어, 프론트가 막대를 눌렀을 때
     *                  오늘 날짜로 연도를 <b>추측</b>해야 했다. 창이 미래 달을 품으면 9월을 눌렀는데
     *                  작년 9월로 가는 식으로 틀린다. 연도를 그대로 실어 보내 추측을 없앤다.
     */
    public record MonthlyData(
            String yearMonth,
            String label,
            Long amount
    ) {}
}
