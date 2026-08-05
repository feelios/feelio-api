package com.korit.feelioapi.domain.summary.service;

import com.korit.feelioapi.domain.analysis.service.SpendStatus;
import org.springframework.stereotype.Component;

/**
 * 규칙기반 말랑이 코멘트(무료·즉시·결정적).
 *
 * <p>GPT 를 켜도 실패·타임아웃·빈 응답 시 이 결과가 대신 나가므로 항상 살아 있어야 한다.
 * 그래서 {@link org.springframework.boot.autoconfigure.condition.ConditionalOnProperty} 를 걸지 않는다.
 *
 * <p>말투는 홈 말랑이답게 부드러운 반말이고, 평가 문장에는 반드시 수치가 하나 들어간다.
 */
@Component
public class RuleMallangCommentGenerator implements MallangCommentGenerator {

    @Override
    public MallangComment generate(SpendStatus status, long expense, long budget, int usageRate) {
        return new MallangComment(evaluation(status, expense, usageRate), encouragement(status));
    }

    private String evaluation(SpendStatus status, long expense, int usageRate) {
        return switch (status) {
            // 지출이 0원이면 소진율이 의미가 없어 금액만 말한다.
            case ZERO -> "이번 달은 아직 쓴 게 없어.";
            // 예산을 못 구하는 경우도 근거 수치는 남긴다(지출액).
            case NO_BUDGET -> String.format("이번 달 %,d원 썼어.", expense);
            case OVER -> String.format("이번 달 %,d원 썼어. 예산의 %d%%야.", expense, usageRate);
            case WARNING -> String.format("이번 달 %,d원 썼어. 예산의 %d%%까지 왔어.", expense, usageRate);
            case SAVING -> String.format("이번 달 %,d원 썼어. 예산의 %d%%라 아직 여유 있어.", expense, usageRate);
        };
    }

    private String encouragement(SpendStatus status) {
        return switch (status) {
            case ZERO -> "첫 기록을 남겨보면 내가 흐름을 읽어줄게.";
            case NO_BUDGET -> "목표를 하나 정해두면 예산도 같이 잡아줄게.";
            case OVER -> "남은 날은 조금만 천천히 가볼까?";
            case WARNING -> "이번 주는 한 번만 아껴봐도 충분해.";
            case SAVING -> "지금 흐름 그대로 가면 돼.";
        };
    }
}
