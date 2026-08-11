package com.korit.feelioapi.domain.universe.dto;

import java.util.List;

/**
 * GET /api/universe/simulation 응답 data (API-CONTRACT §9).
 * REDUCED 는 소비가 가장 몰린 카테고리(topCategory) 지출만 reductionRate 만큼 줄인 시나리오다.
 */
public record UniverseResponse(
        GoalSummaryDto goal,
        Long monthlyIncome,
        Long monthlyExpense,
        TopCategoryDto topCategory,
        double reductionRate,
        List<ScenarioDto> scenarios
) {
}
