package com.korit.feelioapi.domain.summary.service;

import com.korit.feelioapi.domain.summary.dto.EmotionSummaryDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대표 감정 선정 규칙 (A12-3).
 *
 * <p>OpenAI 호출 없이 선정 로직만 검증한다. 특히 동률 처리는 캐시 키 안정성과 직결돼
 * 여기서 막지 못하면 호출할 때마다 GPT 가 다시 불린다.
 */
class EmotionContextTest {

    @Test
    void 기록_횟수가_가장_많은_감정을_고른다() {
        EmotionContext context = EmotionContext.of(
                List.of(
                        new EmotionSummaryDto(1L, "신남", 3, 60_000L),
                        new EmotionSummaryDto(4L, "스트레스", 9, 180_000L),
                        new EmotionSummaryDto(7L, "평온", 5, 40_000L)),
                List.of());

        assertThat(context.hasEmotion()).isTrue();
        assertThat(context.name()).isEqualTo("스트레스");
        assertThat(context.count()).isEqualTo(9);
        assertThat(context.amount()).isEqualTo(180_000L);
    }

    @Test
    void 횟수가_같으면_지출액이_큰_감정을_고른다() {
        EmotionContext context = EmotionContext.of(
                List.of(
                        new EmotionSummaryDto(1L, "신남", 5, 60_000L),
                        new EmotionSummaryDto(4L, "스트레스", 5, 180_000L)),
                List.of());

        assertThat(context.name()).isEqualTo("스트레스");
    }

    @Test
    void 횟수와_지출액이_모두_같으면_순서가_흔들려도_같은_감정을_고른다() {
        List<EmotionSummaryDto> emotions = Arrays.asList(
                new EmotionSummaryDto(1L, "신남", 5, 60_000L),
                new EmotionSummaryDto(4L, "스트레스", 5, 60_000L));

        String first = EmotionContext.of(emotions, List.of()).name();

        List<EmotionSummaryDto> reversed = new ArrayList<>(emotions);
        java.util.Collections.reverse(reversed);
        String second = EmotionContext.of(reversed, List.of()).name();

        // 목록 순서가 바뀌어도 결과가 같아야 캐시 키가 안정적이다
        assertThat(first).isEqualTo(second);
    }

    @Test
    void 기록이_없으면_빈_컨텍스트다() {
        assertThat(EmotionContext.of(List.of(), List.of()).hasEmotion()).isFalse();
        assertThat(EmotionContext.of(null, null).hasEmotion()).isFalse();
        assertThat(EmotionContext.of(List.of(), List.of()).name()).isNull();
    }

    @Test
    void 횟수가_0인_감정만_있으면_대표_감정으로_치지_않는다() {
        EmotionContext context = EmotionContext.of(
                List.of(new EmotionSummaryDto(1L, "신남", 0, 0L)),
                List.of());

        assertThat(context.hasEmotion()).isFalse();
    }

    @Test
    void 지난달_1위와_같으면_REPEATED_다르면_CHANGED_비교불가면_UNKNOWN() {
        List<EmotionSummaryDto> current = List.of(new EmotionSummaryDto(4L, "스트레스", 9, 180_000L));

        assertThat(EmotionContext.of(current, List.of(new EmotionSummaryDto(4L, "스트레스", 6, 90_000L))).trend())
                .isEqualTo(EmotionContext.Trend.REPEATED);
        assertThat(EmotionContext.of(current, List.of(new EmotionSummaryDto(1L, "신남", 6, 90_000L))).trend())
                .isEqualTo(EmotionContext.Trend.CHANGED);
        assertThat(EmotionContext.of(current, List.of()).trend())
                .isEqualTo(EmotionContext.Trend.UNKNOWN);
    }
}
