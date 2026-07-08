package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.CategoryStatDto;
import com.korit.feelioapi.domain.analysis.dto.EmotionStatDto;
import com.korit.feelioapi.domain.analysis.dto.InsightDto;
import com.korit.feelioapi.domain.analysis.dto.TimeSlotStatDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedInsightGeneratorTest {

    private final RuleBasedInsightGenerator generator = new RuleBasedInsightGenerator();

    @Test
    void 감정_시간대_카테고리_인사이트를_생성한다() {
        List<InsightDto> insights = generator.generate(
                2026, 7,
                List.of(new EmotionStatDto(2L, "설렘", "#F28AB7", 140600L, 6L)),
                List.of(new CategoryStatDto(3L, "카페", "EXPENSE", 48000L, 6L)),
                List.of(new TimeSlotStatDto("NIGHT", "밤", 190000L, 8L))
        );

        assertThat(insights).extracting(InsightDto::type)
                .containsExactly("EMOTION_FOCUS", "TIME_PATTERN", "TOP_CATEGORY");
        // 긍정 감정(설렘)도 대상 — 감정 중립 문구
        assertThat(insights.get(0).content()).contains("설렘");
        assertThat(insights.get(1).content()).contains("밤");
        assertThat(insights.get(2).content()).contains("카페");
    }

    @Test
    void 집계가_비면_인사이트도_비어있다() {
        List<InsightDto> insights = generator.generate(2026, 7, List.of(), List.of(), List.of());
        assertThat(insights).isEmpty();
    }
}
