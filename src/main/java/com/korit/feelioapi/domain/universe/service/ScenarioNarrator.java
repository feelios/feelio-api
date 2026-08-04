package com.korit.feelioapi.domain.universe.service;

import java.util.List;

/**
 * 평행우주 시나리오의 문장(narration)을 만든다.
 * 구현 교체 지점: 규칙기반(RuleBasedScenarioNarrator) ↔ GPT(GptScenarioNarrator).
 *
 * 숫자 필드(monthlyExpense·monthlySaving·monthsToGoal·estimatedAchieveDate)는 계약 §9 계산식대로
 * 호출 측이 이미 구한 값이다. 이 인터페이스는 문장만 책임진다.
 */
public interface ScenarioNarrator {

    /**
     * CURRENT·REDUCED 두 문장을 그 순서로 돌려준다.
     * 반환 크기는 항상 2다 — 두 시나리오가 고정이라 개수를 맞추지 못하면 구현체가 폴백해야 한다.
     */
    List<String> narrate(NarrationContext context);
}
