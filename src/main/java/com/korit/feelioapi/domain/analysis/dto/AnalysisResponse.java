package com.korit.feelioapi.domain.analysis.dto;

import java.util.List;

/**
 * GET /api/analysis/monthly 응답 data (API-CONTRACT §9). 집계는 지출 기준.
 */
public record AnalysisResponse(
        int year,
        int month,
        Long totalIncome,
        Long totalExpense,
        List<CategoryStatDto> byCategory,
        List<EmotionStatDto> byEmotion,
        List<TimeSlotStatDto> byTimeSlot,
        List<InsightDto> insights
) {
}
