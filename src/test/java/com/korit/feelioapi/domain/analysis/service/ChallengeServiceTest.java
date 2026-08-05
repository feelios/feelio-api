package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.CategoryStatDto;
import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ChallengeServiceTest {

    @Test
    void GPT가_비활성화되면_외부호출_없이_준비중_문구를_반환한다() {
        RuleBasedInsightCardGenerator fallback = new RuleBasedInsightCardGenerator();
        ChallengeService service = new ChallengeService(fallback);
        List<CategoryStatDto> categories = List.of(
                new CategoryStatDto(1L, "배달", "EXPENSE", 100000L, 4L));

        String result = service.generate(categories);

        assertThat(result).isEqualTo("배달 소비 3일 참아보기");
    }

    @Test
    void 주간_기록이_없으면_GPT설정이어도_외부호출을_하지_않는다() {
        RuleBasedInsightCardGenerator fallback = new RuleBasedInsightCardGenerator();
        ChallengeService service = new ChallengeService(fallback);

        assertThat(service.generate(List.of())).isEqualTo("며칠만 기록을 이어가 보기");
    }

    @Test
    void 목록이_null_이어도_안전하게_폴백한다() {
        ChallengeService service = new ChallengeService(new RuleBasedInsightCardGenerator());

        assertThat(service.generate(null)).isEqualTo("며칠만 기록을 이어가 보기");
    }
}
