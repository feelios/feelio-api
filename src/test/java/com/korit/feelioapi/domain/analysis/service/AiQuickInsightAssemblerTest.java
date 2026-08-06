package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.AiInsightsResponse.AiQuickInsight;
import com.korit.feelioapi.domain.analysis.dto.AiInsightsResponse.EmotionCard;
import com.korit.feelioapi.domain.analysis.dto.CategoryStatDto;
import com.korit.feelioapi.domain.analysis.dto.EmotionStatDto;
import com.korit.feelioapi.domain.analysis.dto.TimeSlotStatDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * 문장 생성은 각 정본 서비스 소관이므로 여기서는 **숫자 판정과 카드 구조**만 검증한다.
 * 감정 카드 문구는 규칙기반 구현을 물리고, 팩트·챌린지는 외부 호출(GPT)이 얽혀 있어 목으로 대체한다.
 */
class AiQuickInsightAssemblerTest {

    private final FactReportService factReportService = mock(FactReportService.class);
    private final ChallengeService challengeService = mock(ChallengeService.class);

    private final AiQuickInsightAssembler assembler =
            new AiQuickInsightAssembler(new RuleBasedInsightCardGenerator(), factReportService, challengeService);

    {
        lenient().when(factReportService.generate(any(), anyLong(), anyLong(), any())).thenReturn("팩트 문장");
        lenient().when(challengeService.generate(any())).thenReturn("챌린지 문장");
    }

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

    private List<AiQuickInsight> assemble(long expense, long budget) {
        return assembler.assembleQuickInsights(byEmotion, byCategory, byTimeSlot, List.of(), expense, budget);
    }

    @Test
    void 프론트가_기대하는_라벨과_타입으로_4개를_만든다() {
        List<AiQuickInsight> result = assemble(1_000_000L, 2_000_000L);

        assertThat(result).extracting(AiQuickInsight::getLabel)
                .containsExactly("위험 루트", "팩트 리포트", "소비 위험도", "AI 맞춤 챌린지");
        assertThat(result).extracting(AiQuickInsight::getType)
                .containsExactly("default", "fact", "risk", "default");
    }

    @Test
    void 위험루트는_지출이_가장_큰_시간대_감정_카테고리를_잇는다() {
        AiQuickInsight route = assemble(1_000_000L, 2_000_000L).get(0);

        // 시간대는 목록 순서가 아니라 금액 최대(새벽 800,000)가 뽑혀야 한다
        assertThat(route.getValue()).isEqualTo("새벽 · 무덤덤 · 패션/미용");
        assertThat(route.getNote()).isEqualTo("7건");
    }

    @Test
    void 예산의_90퍼센트_이상_쓰면_위험이다() {
        AiQuickInsight risk = assemble(950_000L, 1_000_000L).get(2);

        assertThat(risk.getValue()).isEqualTo("위험");
        assertThat(risk.getNote()).isEqualTo("예산의 95% 사용");
    }

    @Test
    void 예산의_70퍼센트_이상_90퍼센트_미만이면_주의다() {
        AiQuickInsight risk = assemble(700_000L, 1_000_000L).get(2);

        assertThat(risk.getValue()).isEqualTo("주의");
        assertThat(risk.getNote()).isEqualTo("예산의 70% 사용");
    }

    @Test
    void 예산의_70퍼센트_미만이면_안전이다() {
        assertThat(assemble(300_000L, 1_000_000L).get(2).getValue()).isEqualTo("안전");
    }

    @Test
    void 예산을_산출할_수_없으면_예산_미설정으로_표시한다() {
        // 활성 목표가 없거나 전월 기록이 없으면 예산이 0 이라 비율 판정 자체가 불가능하다.
        AiQuickInsight risk = assemble(1_000_000L, 0L).get(2);

        assertThat(risk.getValue()).isEqualTo("예산 미설정");
        assertThat(risk.getNote()).isEqualTo("목표를 정하면 예산이 잡혀요");
    }

    @Test
    void 지출_기록이_없으면_빈_리스트를_준다() {
        // 억지 문구 대신 프론트의 빈 상태 표시에 맡긴다.
        assertThat(assembler.assembleQuickInsights(List.of(), List.of(), List.of(), List.of(), 0L, 0L)).isEmpty();
    }

    @Test
    void 감정카드는_상위_3건까지_감정_순서대로_만든다() {
        List<EmotionCard> cards = assembler.assembleEmotionCards(byEmotion, byCategory, byTimeSlot);

        assertThat(cards).hasSize(3);
        assertThat(cards).extracting(EmotionCard::getTitle)
                .containsExactly("'무덤덤'일 때의 소비", "'설렘'일 때의 소비", "'스트레스'일 때의 소비");
        assertThat(cards.get(0).getDesc()).isNotBlank();
    }

    @Test
    void 감정카드는_3건을_넘지_않는다() {
        List<EmotionStatDto> many = List.of(
                new EmotionStatDto(1L, "a", "#1", 100L, 1L),
                new EmotionStatDto(2L, "b", "#2", 90L, 1L),
                new EmotionStatDto(3L, "c", "#3", 80L, 1L),
                new EmotionStatDto(4L, "d", "#4", 70L, 1L)
        );

        assertThat(assembler.assembleEmotionCards(many, byCategory, byTimeSlot)).hasSize(3);
    }

    @Test
    void 감정_기록이_없으면_감정카드도_비운다() {
        assertThat(assembler.assembleEmotionCards(List.of(), byCategory, byTimeSlot)).isEmpty();
    }
}
