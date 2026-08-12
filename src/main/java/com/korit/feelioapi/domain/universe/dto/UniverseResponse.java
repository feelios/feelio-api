package com.korit.feelioapi.domain.universe.dto;

import java.util.List;

/**
 * GET /api/universe/simulation 응답 data (API-CONTRACT §9).
 *
 * REDUCED 는 <b>사용자가 고른 카테고리</b>들의 지출을 reductionRate 만큼 줄인 시나리오다.
 * 고르지 않으면 가장 많이 쓴 카테고리 하나가 기본값이 된다.
 *
 * @param categories      이번 기준 월에 지출이 있는 카테고리 전체 — 화면의 선택지
 * @param focusCategories 그중 실제로 줄이기로 한 것들
 * @param topCategory     focusCategories 의 대표 1건(가장 많이 쓴 것). 태그·문구용
 */
public record UniverseResponse(
        GoalSummaryDto goal,
        Long monthlyIncome,
        Long monthlyExpense,
        TopCategoryDto topCategory,
        List<TopCategoryDto> categories,
        List<TopCategoryDto> focusCategories,
        double reductionRate,
        List<ScenarioDto> scenarios
) {
}
