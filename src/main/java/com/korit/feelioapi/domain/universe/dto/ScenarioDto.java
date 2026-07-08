package com.korit.feelioapi.domain.universe.dto;

/**
 * 미래 시나리오 (API-CONTRACT §9 universe.scenarios). key: CURRENT | REDUCED.
 * monthsToGoal / estimatedAchieveDate 는 도달 불가(월 저축 ≤ 0) 시 null.
 */
public record ScenarioDto(
        String key,
        String title,
        Long monthlyExpense,
        Long monthlySaving,
        Integer monthsToGoal,
        String estimatedAchieveDate,
        String narration
) {
}
