package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.AiInsightsResponse.AiQuickInsight;
import com.korit.feelioapi.domain.analysis.dto.AiInsightsResponse.EmotionCard;
import com.korit.feelioapi.domain.analysis.dto.CategoryStatDto;
import com.korit.feelioapi.domain.analysis.dto.EmotionStatDto;
import com.korit.feelioapi.domain.analysis.dto.TimeSlotStatDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * AI 분석 화면 상단 요약 카드(aiQuickInsights)와 감정 카드 뒷면 문구(emotionCards)를 집계에서 만든다.
 *
 * label·color·type 은 프론트(AnalysisPageDc)가 고정으로 기대하는 값이라 그대로 맞춘다.
 * - label  좌상단 캡션(고정 4종)
 * - note   우상단 짧은 태그
 * - value  아래 굵은 한 줄(실제 내용)
 * - type   default | fact | risk  (risk 는 신호등 UI, fact 는 강조 스타일)
 */
@Component
public class AiQuickInsightAssembler {

    private static final String COLOR_SUB = "var(--sub)";
    private static final String COLOR_POINT = "#E87573";

    /** 전월 대비 지출이 이 비율을 넘게 늘면 '높음'. */
    private static final double RISK_HIGH_RATE = 20.0;
    /** 이 비율을 넘게 줄면 '낮음'. */
    private static final double RISK_LOW_RATE = -20.0;

    private static final int EMOTION_CARD_LIMIT = 3;

    /**
     * 지출 기록이 없으면 빈 리스트를 반환한다.
     * 억지 문구를 만드는 것보다 프론트의 빈 상태 표시에 맡기는 편이 낫다.
     */
    public List<AiQuickInsight> assembleQuickInsights(List<EmotionStatDto> byEmotion,
                                                      List<CategoryStatDto> byCategory,
                                                      List<TimeSlotStatDto> byTimeSlot,
                                                      long currentExpense,
                                                      long previousExpense) {
        if (byEmotion.isEmpty() && byCategory.isEmpty() && byTimeSlot.isEmpty()) {
            return List.of();
        }

        EmotionStatDto topEmotion = byEmotion.isEmpty() ? null : byEmotion.get(0); // amount 내림차순
        CategoryStatDto topCategory = byCategory.isEmpty() ? null : byCategory.get(0);
        TimeSlotStatDto topTimeSlot = byTimeSlot.stream()
                .max(Comparator.comparingLong(TimeSlotStatDto::amount))
                .orElse(null);

        Double changeRate = changeRate(currentExpense, previousExpense);

        List<AiQuickInsight> insights = new ArrayList<>();
        insights.add(riskRoute(topEmotion, topCategory, topTimeSlot));
        insights.add(factReport(currentExpense, changeRate));
        insights.add(riskLevel(changeRate));
        insights.add(challenge(topEmotion, topTimeSlot));
        return insights;
    }

    /** 소비가 몰린 경로 = 시간대 · 감정 · 카테고리 조합. */
    private AiQuickInsight riskRoute(EmotionStatDto topEmotion,
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

        long count = topTimeSlot != null ? topTimeSlot.count() : 0L;
        return AiQuickInsight.builder()
                .label("위험 루트")
                .value(parts.isEmpty() ? "아직 뚜렷한 경로가 없어요" : String.join(" · ", parts))
                .note(count > 0 ? count + "건" : "-")
                .color(COLOR_SUB)
                .type("default")
                .build();
    }

    /** 이번 달 지출 총액 + 전월 대비 증감. */
    private AiQuickInsight factReport(long currentExpense, Double changeRate) {
        return AiQuickInsight.builder()
                .label("팩트 리포트")
                .value(String.format("이번 달 지출 %,d원", currentExpense))
                .note(changeRate == null ? "전월 기록 없음" : String.format("전월 대비 %+.0f%%", changeRate))
                .color(COLOR_POINT)
                .type("fact")
                .build();
    }

    /** 전월 대비 증감률로 3단계 판정. 전월 기록이 없으면 비교 기준이 없으므로 '보통'. */
    private AiQuickInsight riskLevel(Double changeRate) {
        String level;
        String note;
        if (changeRate == null) {
            level = "보통";
            note = "비교할 전월 기록 없음";
        } else if (changeRate > RISK_HIGH_RATE) {
            level = "높음";
            note = String.format("전월보다 %+.0f%%", changeRate);
        } else if (changeRate < RISK_LOW_RATE) {
            level = "낮음";
            note = String.format("전월보다 %+.0f%%", changeRate);
        } else {
            level = "보통";
            note = "전월과 비슷한 수준";
        }

        return AiQuickInsight.builder()
                .label("소비 위험도")
                .value(level)
                .note(note)
                .color(COLOR_POINT)
                .type("risk")
                .build();
    }

    /** 가장 많이 쓴 시간대·감정을 겨냥한 실행 제안. */
    private AiQuickInsight challenge(EmotionStatDto topEmotion, TimeSlotStatDto topTimeSlot) {
        String value;
        if (topTimeSlot != null && topEmotion != null) {
            value = String.format("%s에 '%s' 소비 3일 참아보기", topTimeSlot.label(), topEmotion.name());
        } else if (topTimeSlot != null) {
            value = String.format("%s 시간대 결제 3일 참아보기", topTimeSlot.label());
        } else if (topEmotion != null) {
            value = String.format("'%s'일 때 결제 전 10분 기다리기", topEmotion.name());
        } else {
            value = "며칠만 기록을 이어가 보기";
        }

        return AiQuickInsight.builder()
                .label("AI 맞춤 챌린지")
                .value(value)
                .note("이번 주")
                .color(COLOR_SUB)
                .type("default")
                .build();
    }

    /**
     * 감정 카드 뒷면 문구. 앞면(감정명·비율·금액)은 프론트가 §9 byEmotion 상위 3건으로 그리므로
     * 여기서는 같은 순서·같은 개수로 문구만 맞춰 준다.
     */
    public List<EmotionCard> assembleEmotionCards(List<EmotionStatDto> byEmotion, long currentExpense) {
        List<EmotionCard> cards = new ArrayList<>();
        for (EmotionStatDto emotion : byEmotion) {
            if (cards.size() >= EMOTION_CARD_LIMIT) {
                break;
            }
            long share = currentExpense > 0 ? Math.round(emotion.amount() * 100.0 / currentExpense) : 0L;
            cards.add(EmotionCard.builder()
                    .title(String.format("'%s'일 때의 소비", emotion.name()))
                    .desc(String.format("%d건, %,d원 썼어요. 이번 달 지출의 %d%%예요.",
                            emotion.count(), emotion.amount(), share))
                    .build());
        }
        return cards;
    }

    /** 전월 지출이 0이면 증감률을 정의할 수 없어 null 을 돌려준다. */
    private Double changeRate(long currentExpense, long previousExpense) {
        if (previousExpense <= 0) {
            return null;
        }
        return (currentExpense - previousExpense) * 100.0 / previousExpense;
    }
}
