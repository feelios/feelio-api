package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.EmotionStatDto;
import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class EmotionAnalysisServiceTest {

    @Test
    void GPT가_비활성화되면_외부호출_없이_준비중_문구를_반환한다() {
        OpenAIClient client = mock(OpenAIClient.class);
        EmotionAnalysisService service = new EmotionAnalysisService(client, "gpt-4o-mini", 3L, "rule");
        List<EmotionStatDto> emotions = List.of(
                new EmotionStatDto(4L, "스트레스", "#A68BEA", 150000L, 4L));

        String result = service.generate(emotions, "배달", "밤");

        assertThat(result).isEqualTo(EmotionAnalysisService.FALLBACK_MESSAGE);
        verifyNoInteractions(client);
    }

    @Test
    void 감정_기록이_없으면_GPT설정이어도_외부호출을_하지_않는다() {
        OpenAIClient client = mock(OpenAIClient.class);
        EmotionAnalysisService service = new EmotionAnalysisService(client, "gpt-4o-mini", 3L, "gpt");

        assertThat(service.generate(List.of(), "배달", "밤"))
                .isEqualTo(EmotionAnalysisService.FALLBACK_MESSAGE);
        verifyNoInteractions(client);
    }
}
