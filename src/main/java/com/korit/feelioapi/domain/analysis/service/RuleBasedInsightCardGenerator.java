package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.EmotionStatDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 규칙기반 카드 문구(무료·즉시·결정적). GPT 를 켜도 실패·타임아웃 시 이 결과가 대신 나가므로 항상 살아 있어야 한다.
 * 페르소나 말투는 GPT 쪽이 담당하고, 여기서는 같은 뜻을 담백하게 쓴다 — 폴백이 튀면 오히려 부자연스럽다.
 *
 * factReport·challenge 는 인터페이스 메서드가 아니다(#180). {@link FactReportService}·{@link ChallengeService}
 * 가 이 빈을 구체 타입으로 주입받아 GPT 실패 시 폴백으로 호출한다.
 */
@Component
public class RuleBasedInsightCardGenerator implements InsightCardGenerator {

    /** {@link FactReportService} 의 폴백. */
    public String factReport(SpendStatus status, String topCategory, long expense) {
        return switch (status) {
            case ZERO -> "이번 달은 아직 지출 기록이 없어요.";
            case OVER -> topCategory == null
                    ? "예산이 거의 다 찼어요. 지출을 점검해보세요."
                    : String.format("'%s'에서 유독 지출이 많았어요.", topCategory);
            case WARNING -> "예산의 70%를 넘겼어요. 주의가 필요해요.";
            case SAVING -> "예산 안에서 안정적으로 소비하고 있어요.";
            case NO_BUDGET -> "꾸준히 기록을 남겨 예산 관리를 시작해보세요.";
        };
    }

    /** {@link ChallengeService} 의 폴백. */
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
