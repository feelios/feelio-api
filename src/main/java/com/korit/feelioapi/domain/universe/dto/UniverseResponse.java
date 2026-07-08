package com.korit.feelioapi.domain.universe.dto;

import java.util.List;

/**
 * GET /api/universe/simulation 응답 data (API-CONTRACT §9).
 * "감정소비" = 소비가 가장 몰린 한 감정(focusEmotion). REDUCED 는 그 감정 지출만 reductionRate 만큼 줄인 시나리오.
 */
public record UniverseResponse(
        GoalSummaryDto goal,
        Long monthlyIncome,
        Long monthlyExpense,
        FocusEmotionDto focusEmotion,
        double reductionRate,
        List<ScenarioDto> scenarios
) {
}
