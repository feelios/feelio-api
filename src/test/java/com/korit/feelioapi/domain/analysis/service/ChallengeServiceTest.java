package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.CategoryStatDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChallengeServiceTest {

    private final ChallengeService service = new ChallengeService(new RuleBasedInsightCardGenerator());

    @Test
    void 주간_기록이_있으면_상위_카테고리로_챌린지를_만든다() {
        List<CategoryStatDto> categories = List.of(
                new CategoryStatDto(1L, "배달", "EXPENSE", 100000L, 4L));

        assertThat(service.generate(categories)).isEqualTo("배달 소비 3일 참아보기");
    }

    @Test
    void 주간_기록이_없으면_기록을_권하는_문구를_반환한다() {
        assertThat(service.generate(List.of())).isEqualTo("며칠만 기록을 이어가 보기");
    }

    @Test
    void 목록이_null_이어도_안전하게_폴백한다() {
        assertThat(service.generate(null)).isEqualTo("며칠만 기록을 이어가 보기");
    }
}
