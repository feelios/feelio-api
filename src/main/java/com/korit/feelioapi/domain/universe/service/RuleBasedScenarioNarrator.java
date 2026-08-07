package com.korit.feelioapi.domain.universe.service;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 규칙기반 시나리오 문장(무료·즉시·결정적). GPT 를 켜도 실패·타임아웃 시 이 결과가 대신 나가므로 항상 살아 있어야 한다.
 * A7-3 이전까지 UniverseService 가 직접 만들던 문장을 그대로 옮긴 것이라 출력이 바뀌지 않는다.
 */
@Component
public class RuleBasedScenarioNarrator implements ScenarioNarrator {

    @Override
    public List<List<String>> narrate(NarrationContext context) {
        return List.of(
                List.of(
                    sentence(false, context.currentMonths(), null),
                    "이대로라면 지금 속도를 유지할 것 같아요.",
                    "조금 더 분발하면 어떨까요?"
                ),
                List.of(
                    sentence(true, context.reducedMonths(), context.currentMonths()),
                    "소비를 줄이면 목표 달성이 훨씬 빨라져요!",
                    "평행우주에서는 멋진 결과를 얻었네요."
                )
        );
    }

    /**
     * @param months       해당 시나리오의 도달 개월. null 이면 도달 불가.
     * @param currentMonths REDUCED 가 현행과 비교할 기준. CURRENT 는 비교 대상이 없어 null 이다.
     */
    private String sentence(boolean isReduced, Integer months, Integer currentMonths) {
        if (months != null && months == 0) {
            return "이미 목표 금액을 모았어요.";
        }
        if (months == null) {
            return isReduced ? "지출을 더 줄이면 목표에 다가갈 수 있어요."
                    : "지금 소비 흐름으로는 목표 도달이 어려워요. 조금 줄여볼까요?";
        }
        if (isReduced && currentMonths != null && currentMonths > months) {
            return String.format("이렇게 줄이면 약 %d개월 뒤 도착, %d개월 빨라져요.",
                    months, currentMonths - months);
        }
        return String.format("지금 속도라면 약 %d개월 뒤 목표에 닿아요.", months);
    }
}
