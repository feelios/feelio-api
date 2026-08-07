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
     * CURRENT와 REDUCED 두 개의 시나리오에 해당하는 코멘트 리스트를 반환한다.
     * 반환 크기는 항상 2개이며, 각 항목은 여러 개의 코멘트(롤링용)를 포함하는 리스트이다.
     */
    List<List<String>> narrate(NarrationContext context);
}
