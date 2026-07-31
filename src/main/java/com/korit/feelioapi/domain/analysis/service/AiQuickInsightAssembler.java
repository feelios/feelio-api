package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.AiInsightsResponse.AiQuickInsight;
import com.korit.feelioapi.domain.analysis.dto.AiInsightsResponse.EmotionCard;
import com.korit.feelioapi.domain.analysis.dto.CategoryStatDto;
import com.korit.feelioapi.domain.analysis.dto.EmotionStatDto;
import com.korit.feelioapi.domain.analysis.dto.TimeSlotStatDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * AI 분석 화면 상단 요약 카드(aiQuickInsights)와 감정 카드 뒷면 문구(emotionCards)를 조립한다.
 *
 * 숫자·상태 판정은 여기서 자바로 하고, 문장만 {@link InsightCardGenerator} 에 맡긴다.
 * label·color·type 은 프론트(AnalysisPageDc)가 고정으로 기대하는 값이라 그대로 맞춘다.
 * - label  좌상단 캡션(고정 4종) / note 우상단 태그 / value 본문 한 줄
 * - type   default | fact | risk  (risk 는 신호등 UI)
 */
@Component
@RequiredArgsConstructor
public class AiQuickInsightAssembler {

    private static final String COLOR_SUB = "var(--sub)";
    private static final String COLOR_POINT = "#E87573";
    private static final int EMOTION_CARD_LIMIT = 3;

    private final InsightCardGenerator cardGenerator;

    /**
     * 지출 기록이 없으면 빈 리스트를 반환한다.
     * 억지 문구를 만드는 것보다 프론트의 빈 상태 표시에 맡기는 편이 낫다.
     */
    public List<AiQuickInsight> assembleQuickInsights(List<EmotionStatDto> byEmotion,
                                                      List<CategoryStatDto> byCategory,
                                                      List<TimeSlotStatDto> byTimeSlot,
                                                      long currentExpense,
                                                      long budget) {
        if (byEmotion.isEmpty() && byCategory.isEmpty() && byTimeSlot.isEmpty()) {
            return List.of();
        }

        EmotionStatDto topEmotion = byEmotion.isEmpty() ? null : byEmotion.get(0); // amount 내림차순
        CategoryStatDto topCategory = byCategory.isEmpty() ? null : byCategory.get(0);
        TimeSlotStatDto topTimeSlot = byTimeSlot.stream()
                .max(Comparator.comparingLong(TimeSlotStatDto::amount))
                .orElse(null);

        SpendStatus status = SpendStatus.of(currentExpense, budget);
        String route = riskRouteText(topEmotion, topCategory, topTimeSlot);
        String topCategoryName = topCategory == null ? null : topCategory.name();

        List<AiQuickInsight> insights = new ArrayList<>();
        insights.add(card("위험 루트",
                route == null ? "아직 뚜렷한 경로가 없어요" : route,
                topTimeSlot != null ? topTimeSlot.count() + "건" : "-",
                COLOR_SUB, "default"));
        insights.add(card("팩트 리포트",
                cardGenerator.factReport(status, topCategoryName, currentExpense),
                String.format("이번 달 %,d원", currentExpense),
                COLOR_POINT, "fact"));
        insights.add(riskLevel(status, currentExpense, budget));
        insights.add(card("AI 맞춤 챌린지",
                cardGenerator.challenge(route),
                "이번 주",
                COLOR_SUB, "default"));
        return insights;
    }

    /** 소비가 몰린 경로 = 시간대 · 감정 · 카테고리 조합. 챌린지 프롬프트의 입력이기도 하다. */
    private String riskRouteText(EmotionStatDto topEmotion,
                                 CategoryStatDto topCategory,
                                 TimeSlotStatDto topTimeSlot) {
        List<String> parts = new ArrayList<>();
        if (topTimeSlot != null) {
            parts.add(topTimeSlot.label());
        }
        if (topEmotion != null) {
            parts.add(topEmotion.name());
        }
        if (topCategory != null) {
            parts.add(topCategory.name());
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    /**
     * 예산 소진율로 판정한다(GPT 호출 없음 — 속도·비용 모두 유리하고 결과가 흔들리지 않는다).
     * 프론트 신호등은 value 로 불을 고른다: 위험=Red · 주의=Yellow · 안전=Green.
     */
    private AiQuickInsight riskLevel(SpendStatus status, long expense, long budget) {
        String value = switch (status) {
            case OVER -> "위험";
            case WARNING -> "주의";
            case SAVING, ZERO -> "안전";
            case NO_BUDGET -> "예산 미설정";
        };
        String note = switch (status) {
            case NO_BUDGET -> "목표를 정하면 예산이 잡혀요";
            case ZERO -> "이번 달 지출 없음";
            default -> String.format("예산의 %d%% 사용", Math.round(expense * 100.0 / budget));
        };

        return card("소비 위험도", value, note, COLOR_POINT, "risk");
    }

    private AiQuickInsight card(String label, String value, String note, String color, String type) {
        return AiQuickInsight.builder()
                .label(label).value(value).note(note).color(color).type(type)
                .build();
    }

    /**
     * 감정 카드 뒷면 문구(3단계 분석). 앞면(감정명·비율·금액)은 프론트가 §9 byEmotion 상위 3건으로 그리므로
     * 여기서는 같은 순서·같은 개수로 문구만 맞춰 준다.
     */
    public List<EmotionCard> assembleEmotionCards(List<EmotionStatDto> byEmotion,
                                                  List<CategoryStatDto> byCategory,
                                                  List<TimeSlotStatDto> byTimeSlot) {
        List<EmotionStatDto> targets = byEmotion.size() <= EMOTION_CARD_LIMIT
                ? byEmotion
                : byEmotion.subList(0, EMOTION_CARD_LIMIT);
        if (targets.isEmpty()) {
            return List.of();
        }

        String topCategory = byCategory.isEmpty() ? null : byCategory.get(0).name();
        String topTimeSlot = byTimeSlot.stream()
                .max(Comparator.comparingLong(TimeSlotStatDto::amount))
                .map(TimeSlotStatDto::label)
                .orElse(null);

        List<String> analyses = cardGenerator.emotionAnalyses(targets, topCategory, topTimeSlot);

        List<EmotionCard> cards = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            cards.add(EmotionCard.builder()
                    .title(String.format("'%s'일 때의 소비", targets.get(i).name()))
                    .desc(i < analyses.size() ? analyses.get(i) : "")
                    .build());
        }
        return cards;
    }
}
