package com.korit.feelioapi.domain.universe.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 규칙기반 시나리오 문장(무료·즉시·결정적). GPT 를 켜도 실패·타임아웃 시 이 결과가 대신 나가므로 항상 살아 있어야 한다.
 *
 * <h3>두 우주는 역할이 다르다</h3>
 * 예전에는 양쪽 모두 같은 틀로 사실만 나열했다 — "목표까지 500,000원 남았어요", "매달 0원씩 모으고 있어요".
 * 전부 맞는 말이지만 사용자가 화면에서 이미 보고 있는 숫자를 문장으로 옮긴 것뿐이라, 읽어도 알게 되는 게 없었다.
 * 이 화면은 분석 화면이고, 두 우주를 나란히 두는 이유는 <b>비교</b>에 있다.
 *
 * <p><b>CURRENT(지금처럼 쓰는 미래)는 짚어 준다.</b> 이대로 두면 얼마나 걸리는지, 무엇이 발목을 잡는지,
 * 그래서 무엇을 하면 되는지. 기분 좋으라고 있는 칸이 아니다.
 *
 * <p><b>REDUCED(줄인 미래)는 보상을 보여준다.</b> 줄이면 얼마나 당겨지는지, 매달 얼마가 더 남는지.
 * "이렇게 하면 이만큼 빨라진다"가 읽혀야 한다.
 */
@Component
public class RuleBasedScenarioNarrator implements ScenarioNarrator {

    /** 목표 이름이 없을 때만 쓰는 대체어. 이름이 있으면 언제나 이름을 부른다. */
    private static final String UNNAMED_GOAL = "목표";

    @Override
    public List<List<String>> narrate(NarrationContext context) {
        String goal = goalLabel(context.goalName());
        return List.of(current(context, goal), reduced(context, goal));
    }

    /**
     * 지금처럼 쓰는 미래. [언제 닿는지] → [무엇이 발목을 잡는지] → [그래서 무엇을 할지].
     * 세 문장이 하나의 진단으로 이어지게 둔다.
     */
    private List<String> current(NarrationContext context, String goal) {
        Integer months = context.currentMonths();
        String focus = context.focusCategoryName();

        String verdict;
        if (months != null && months == 0) {
            verdict = "이미 " + goal + " 목표 금액을 모았어요.";
        } else if (months == null) {
            // 수입보다 지출이 많아 모이는 돈이 없는 상태. 개월 수를 말할 수가 없다.
            verdict = String.format("이대로면 %s에 닿지 못해요. 쓰는 돈이 버는 돈을 넘고 있어요.", goal);
        } else {
            verdict = String.format(Locale.KOREA, "이대로 쓰면 %s까지 %d개월 걸려요.", goal, months);
        }

        String cause = focus == null
                ? String.format(Locale.KOREA, "%s까지 %,d원이 남아 있어요.", goal, context.remaining())
                : String.format("발목을 잡는 건 %s 지출이에요. 이번 달에 가장 많이 썼어요.", focus);

        String advice;
        if (months != null && months == 0) {
            advice = "다음 목표를 세워 볼까요?";
        } else if (focus == null) {
            advice = String.format(Locale.KOREA, "지금은 매달 %,d원씩 모으고 있어요.", context.currentSaving());
        } else {
            // 조사가 변하지 않게 "지출을" 로 붙인다(배달을/카페를 대신).
            advice = String.format("%s 지출을 줄이는 게 가장 빠른 길이에요.", focus);
        }

        return List.of(verdict, cause, advice);
    }

    /**
     * 줄인 미래. [얼마나 빨라지는지] → [매달 얼마가 더 남는지] → [그 돈이 무엇을 앞당기는지].
     */
    private List<String> reduced(NarrationContext context, String goal) {
        Integer months = context.reducedMonths();
        Integer currentMonths = context.currentMonths();
        String focus = context.focusCategoryName();

        String verdict;
        if (months != null && months == 0) {
            verdict = "이미 " + goal + " 목표 금액을 모았어요.";
        } else if (months == null) {
            verdict = String.format("조금 더 줄이면 %s에 닿을 수 있어요.", goal);
        } else if (currentMonths != null && currentMonths > months) {
            // 목표 이름을 넣는다. GPT 가 죽으면 이 문장이 그대로 나가는데, 이름이 없으면
            // 어떤 목표 이야기인지 알 수 없어 폴백에서 AI 를 붙인 의미가 사라진다.
            verdict = String.format("이렇게 줄이면 %d개월 뒤 %s 도착, %d개월 빨라져요.",
                    months, goal, currentMonths - months);
        } else {
            verdict = String.format("이렇게 줄이면 %d개월 뒤 %s에 닿아요.", months, goal);
        }

        String gain = focus == null
                ? String.format(Locale.KOREA, "줄이면 매달 %,d원씩 모으게 돼요.", context.reducedSaving())
                : String.format(Locale.KOREA, "%s 지출을 줄이면 매달 %,d원이 더 남아요.",
                        focus, context.savedPerMonth());

        String effect = months != null && months == 0
                ? "다음 목표에도 같은 속도를 이어가 봐요."
                : String.format(Locale.KOREA, "그 돈이 남은 %,d원을 앞당겨 줘요.", context.remaining());

        return List.of(verdict, gain, effect);
    }

    /**
     * 목표 이름을 문장에 넣을 형태로 고른다.
     * GPT 가 죽으면 이 문장들이 그대로 화면에 나간다. 여기서 '목표'라고만 말하면
     * 어떤 목표 이야기인지 알 수 없어, AI 를 붙인 의미가 폴백에서 사라진다.
     */
    private String goalLabel(String goalName) {
        return (goalName == null || goalName.isBlank()) ? UNNAMED_GOAL : goalName.trim();
    }
}
