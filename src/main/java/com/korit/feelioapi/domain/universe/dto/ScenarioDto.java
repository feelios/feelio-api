package com.korit.feelioapi.domain.universe.dto;

/**
 * 미래 시나리오 (API-CONTRACT §9 universe.scenarios). key: CURRENT | REDUCED.
 * monthsToGoal / daysToGoal / estimatedAchieveDate 는 도달 불가(월 저축 ≤ 0) 시 null.
 */
public record ScenarioDto(
        String key,
        String title,
        Long monthlyExpense,
        Long monthlySaving,
        Integer monthsToGoal,
        /**
         * 도달까지 남은 일수.
         *
         * monthsToGoal 은 올림이라 한 달 안쪽에서는 차이를 담지 못한다. 0.90개월과
         * 0.84개월이 둘 다 1개월이 되어, 저축을 더 하는 시나리오가 같은 시점에
         * 닿는 것처럼 보인다. 화면이 스스로 모순을 말하게 되므로 더 잔 단위를 함께 준다.
         */
        Integer daysToGoal,
        String estimatedAchieveDate,
        java.util.List<String> narrations
) {
}
