package com.korit.feelioapi.domain.universe.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 규칙기반 시나리오 문장(무료·즉시·결정적). GPT 를 켜도 실패·타임아웃 시 이 결과가 대신 나가므로 항상 살아 있어야 한다.
 * 그래서 이 문장들도 GPT 와 같은 기준을 지킨다 — 목표 이름을 부르고, 소비와 도달 시점만 말한다.
 */
@Component
public class RuleBasedScenarioNarrator implements ScenarioNarrator {

    /** 목표 이름이 없을 때만 쓰는 대체어. 이름이 있으면 언제나 이름을 부른다. */
    private static final String UNNAMED_GOAL = "목표";

    /**
     * 세 코멘트 모두 소비와 도달 시점 이야기다.
     * 예전에는 "한 걸음씩 가까워지고 있어요" 같은 응원 문구가 섞여 있었는데,
     * 소비 시뮬레이션 화면에서 근거 없는 덕담은 자리만 차지한다. 숫자로 말한다.
     */
    @Override
    public List<List<String>> narrate(NarrationContext context) {
        String goal = goalLabel(context.goalName());
        String focus = context.focusCategoryName();
        return List.of(
                List.of(
                    sentence(false, context.currentMonths(), null, goal),
                    String.format(Locale.KOREA, "%s까지 %,d원 남았어요.", goal, context.remaining()),
                    String.format(Locale.KOREA, "지금은 매달 %,d원씩 모으고 있어요.", context.currentSaving())
                ),
                List.of(
                    sentence(true, context.reducedMonths(), context.currentMonths(), goal),
                    focus == null
                        ? String.format(Locale.KOREA, "줄이면 매달 모으는 돈이 %,d원이 돼요.", context.reducedSaving())
                        : // 받침 유무에 따라 을/를 이 갈리므로 조사가 변하지 않는 "지출을" 로 붙인다(배달을/카페를 대신).
                          String.format(Locale.KOREA, "%s 지출을 줄이면 매달 모으는 돈이 %,d원이 돼요.", focus, context.reducedSaving()),
                    String.format("그만큼 %s 도착이 앞당겨져요.", goal)
                )
        );
    }

    /**
     * 목표 이름을 문장에 넣을 형태로 고른다.
     * GPT 가 죽으면 이 문장들이 그대로 화면에 나간다. 여기서 '목표'라고만 말하면
     * 어떤 목표 이야기인지 알 수 없어, AI 를 붙인 의미가 폴백에서 사라진다.
     */
    private String goalLabel(String goalName) {
        return (goalName == null || goalName.isBlank()) ? UNNAMED_GOAL : goalName.trim();
    }

    /**
     * @param months       해당 시나리오의 도달 개월. null 이면 도달 불가.
     * @param currentMonths REDUCED 가 현행과 비교할 기준. CURRENT 는 비교 대상이 없어 null 이다.
     * @param goal         문장에 넣을 목표 이름.
     */
    private String sentence(boolean isReduced, Integer months, Integer currentMonths, String goal) {
        if (months != null && months == 0) {
            return "이미 " + goal + " 목표 금액을 모았어요.";
        }
        if (months == null) {
            return isReduced ? "지출을 더 줄이면 " + goal + "에 다가갈 수 있어요."
                    : "지금 소비 흐름으로는 " + goal + " 도달이 어려워요. 조금 줄여볼까요?";
        }
        if (isReduced && currentMonths != null && currentMonths > months) {
            return String.format("이렇게 줄이면 약 %d개월 뒤 %s 도착, %d개월 빨라져요.",
                    months, goal, currentMonths - months);
        }
        return String.format("지금 속도라면 약 %d개월 뒤 %s에 닿아요.", months, goal);
    }
}
