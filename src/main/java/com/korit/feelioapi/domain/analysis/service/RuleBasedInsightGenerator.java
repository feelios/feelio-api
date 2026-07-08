package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.CategoryStatDto;
import com.korit.feelioapi.domain.analysis.dto.EmotionStatDto;
import com.korit.feelioapi.domain.analysis.dto.InsightDto;
import com.korit.feelioapi.domain.analysis.dto.TimeSlotStatDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 규칙기반 인사이트 생성(오프라인·무료·결정적). AI 연동 없이 집계에서 템플릿 문장을 만든다.
 * "감정소비"는 긍정·부정 무관하게 소비가 몰린 감정을 짚는 개념(§9)이므로 문구도 감정 중립이다.
 * 추후 AI 구현으로 교체 시 이 클래스를 대체(또는 @Primary 로 우선순위 지정)한다.
 */
@Component
public class RuleBasedInsightGenerator implements InsightGenerator {

    @Override
    public List<InsightDto> generate(int year,
                                     int month,
                                     List<EmotionStatDto> byEmotion,
                                     List<CategoryStatDto> byCategory,
                                     List<TimeSlotStatDto> byTimeSlot) {
        List<InsightDto> insights = new ArrayList<>();

        if (!byEmotion.isEmpty()) {
            EmotionStatDto top = byEmotion.get(0); // amount 내림차순
            insights.add(new InsightDto("EMOTION_FOCUS",
                    String.format("이번 달은 '%s'일 때 가장 많이 썼어요 (₩%,d).", top.name(), top.amount())));
        }

        byTimeSlot.stream()
                .max(Comparator.comparingLong(TimeSlotStatDto::amount))
                .ifPresent(top -> insights.add(new InsightDto("TIME_PATTERN",
                        String.format("주로 %s 시간대에 소비했어요.", top.label()))));

        if (!byCategory.isEmpty()) {
            CategoryStatDto top = byCategory.get(0);
            insights.add(new InsightDto("TOP_CATEGORY",
                    String.format("가장 많이 쓴 카테고리는 '%s'예요.", top.name())));
        }

        return insights;
    }
}
