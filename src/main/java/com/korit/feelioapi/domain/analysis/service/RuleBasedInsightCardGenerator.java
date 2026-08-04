package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.EmotionStatDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 규칙기반 카드 문구(무료·즉시·결정적). GPT 를 켜도 실패·타임아웃 시 이 결과가 대신 나가므로 항상 살아 있어야 한다.
 * 페르소나 말투는 GPT 쪽이 담당하고, 여기서는 같은 뜻을 담백하게 쓴다 — 폴백이 튀면 오히려 부자연스럽다.
 */
@Component
public class RuleBasedInsightCardGenerator implements InsightCardGenerator {

    @Override
    public String factReport(SpendStatus status, String topCategory, long expense) {
        return switch (status) {
            case ZERO -> "이번 달은 아직 지출 기록이 없어요.";
            case OVER -> topCategory == null
                    ? String.format("이번 달 지출 %,d원. 예산이 거의 다 찼어요.", expense)
                    : String.format("'%s'에서 많이 나갔어요. 이번 달 지출 %,d원.", topCategory, expense);
            case WARNING -> String.format("이번 달 지출 %,d원. 예산의 70%%를 넘겼어요.", expense);
            case SAVING -> String.format("이번 달 지출 %,d원. 예산 안에서 잘 가고 있어요.", expense);
            case NO_BUDGET -> String.format("이번 달 지출 %,d원.", expense);
        };
    }

    @Override
    public String challenge(String riskRoute) {
        if (riskRoute == null || riskRoute.isBlank()) {
            return "며칠만 기록을 이어가 보기";
        }
        return riskRoute + " 소비 3일 참아보기";
    }

    @Override
    public List<String> emotionAnalyses(List<EmotionStatDto> emotions, String topCategory, String topTimeSlotLabel) {
        List<String> analyses = new ArrayList<>();
        for (EmotionStatDto emotion : emotions) {
            String where = topCategory == null ? "" : String.format(" 특히 '%s' 쪽이 많았어요.", topCategory);
            analyses.add(String.format(
                    "'%s'일 때 %d건, %,d원을 썼어요.%s "
                            + "그때의 소비는 단순한 지출이라기보다 그 순간의 마음이 함께 움직인 것으로 보여요. "
                            + "이 패턴을 알고 있으면 다음 소비를 더 의식적으로 선택하는 데 도움이 돼요.",
                    emotion.name(), emotion.count(), emotion.amount(), where));
        }
        return analyses;
    }
}
