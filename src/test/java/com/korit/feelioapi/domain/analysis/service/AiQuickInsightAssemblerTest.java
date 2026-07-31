package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.AiInsightsResponse.AiQuickInsight;
import com.korit.feelioapi.domain.analysis.dto.AiInsightsResponse.EmotionCard;
import com.korit.feelioapi.domain.analysis.dto.CategoryStatDto;
import com.korit.feelioapi.domain.analysis.dto.EmotionStatDto;
import com.korit.feelioapi.domain.analysis.dto.TimeSlotStatDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiQuickInsightAssemblerTest {

    private final AiQuickInsightAssembler assembler = new AiQuickInsightAssembler();

    private final List<EmotionStatDto> byEmotion = List.of(
            new EmotionStatDto(3L, "무덤덤", "#B0B0B0", 600_000L, 6L),
            new EmotionStatDto(2L, "설렘", "#F28AB7", 300_000L, 3L),
            new EmotionStatDto(4L, "스트레스", "#A68BEA", 100_000L, 1L)
    );
    private final List<CategoryStatDto> byCategory = List.of(
            new CategoryStatDto(3L, "패션/미용", "EXPENSE", 500_000L, 4L)
    );
    private final List<TimeSlotStatDto> byTimeSlot = List.of(
            new TimeSlotStatDto("MORNING", "아침", 200_000L, 3L),
            new TimeSlotStatDto("DAWN", "새벽", 800_000L, 7L)
    );

    @Test
    void 프론트가_기대하는_라벨과_타입으로_4개를_만든다() {
        List<AiQuickInsight> result =
                assembler.assembleQuickInsights(byEmotion, byCategory, byTimeSlot, 1_000_000L, 500_000L);

        assertThat(result).extracting(AiQuickInsight::getLabel)
                .containsExactly("위험 루트", "팩트 리포트", "소비 위험도", "AI 맞춤 챌린지");
        assertThat(result).extracting(AiQuickInsight::getType)
                .containsExactly("default", "fact", "risk", "default");
    }

    @Test
    void 위험루트는_지출이_가장_큰_시간대_감정_카테고리를_잇는다() {
        List<AiQuickInsight> result =
                assembler.assembleQuickInsights(byEmotion, byCategory, byTimeSlot, 1_000_000L, 500_000L);

        AiQuickInsight route = result.get(0);
        // 시간대는 amount 최대(새벽 800,000)가 뽑혀야 한다 — 목록 순서가 아니라 금액 기준
        assertThat(route.getValue()).isEqualTo("새벽 · 무덤덤 · 패션/미용");
        assertThat(route.getNote()).isEqualTo("7건");
    }

    @Test
    void 팩트리포트는_이번달_지출과_전월_대비_증감을_보여준다() {
        List<AiQuickInsight> result =
                assembler.assembleQuickInsights(byEmotion, byCategory, byTimeSlot, 1_000_000L, 500_000L);

        assertThat(result.get(1).getValue()).isEqualTo("이번 달 지출 1,000,000원");
        assertThat(result.get(1).getNote()).isEqualTo("전월 대비 +100%");
    }

    @Test
    void 전월보다_크게_늘면_위험도가_높음이다() {
        List<AiQuickInsight> result =
                assembler.assembleQuickInsights(byEmotion, byCategory, byTimeSlot, 1_000_000L, 500_000L);

        assertThat(result.get(2).getValue()).isEqualTo("높음");
    }

    @Test
    void 전월과_비슷하면_위험도가_보통이다() {
        List<AiQuickInsight> result =
                assembler.assembleQuickInsights(byEmotion, byCategory, byTimeSlot, 1_000_000L, 1_000_000L);

        assertThat(result.get(2).getValue()).isEqualTo("보통");
        assertThat(result.get(2).getNote()).isEqualTo("전월과 비슷한 수준");
    }

    @Test
    void 전월보다_크게_줄면_위험도가_낮음이다() {
        List<AiQuickInsight> result =
                assembler.assembleQuickInsights(byEmotion, byCategory, byTimeSlot, 500_000L, 1_000_000L);

        assertThat(result.get(2).getValue()).isEqualTo("낮음");
    }

    @Test
    void 전월_기록이_없으면_증감을_계산하지_않는다() {
        List<AiQuickInsight> result =
                assembler.assembleQuickInsights(byEmotion, byCategory, byTimeSlot, 1_000_000L, 0L);

        assertThat(result.get(1).getNote()).isEqualTo("전월 기록 없음");
        assertThat(result.get(2).getValue()).isEqualTo("보통");
        assertThat(result.get(2).getNote()).isEqualTo("비교할 전월 기록 없음");
    }

    @Test
    void 지출_기록이_없으면_빈_리스트를_준다() {
        // 억지 문구 대신 프론트의 빈 상태 표시에 맡긴다.
        List<AiQuickInsight> result =
                assembler.assembleQuickInsights(List.of(), List.of(), List.of(), 0L, 0L);

        assertThat(result).isEmpty();
    }

    @Test
    void 감정카드는_상위_3건까지_비중과_함께_만든다() {
        List<EmotionCard> cards = assembler.assembleEmotionCards(byEmotion, 1_000_000L);

        assertThat(cards).hasSize(3);
        assertThat(cards.get(0).getTitle()).isEqualTo("'무덤덤'일 때의 소비");
        assertThat(cards.get(0).getDesc()).isEqualTo("6건, 600,000원 썼어요. 이번 달 지출의 60%예요.");
    }

    @Test
    void 감정카드는_3건을_넘지_않는다() {
        List<EmotionStatDto> many = List.of(
                new EmotionStatDto(1L, "a", "#1", 100L, 1L),
                new EmotionStatDto(2L, "b", "#2", 90L, 1L),
                new EmotionStatDto(3L, "c", "#3", 80L, 1L),
                new EmotionStatDto(4L, "d", "#4", 70L, 1L)
        );

        assertThat(assembler.assembleEmotionCards(many, 340L)).hasSize(3);
    }
}
