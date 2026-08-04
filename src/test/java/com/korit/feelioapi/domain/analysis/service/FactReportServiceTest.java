package com.korit.feelioapi.domain.analysis.service;

import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class FactReportServiceTest {

    @Test
    void GPT가_비활성화되면_외부호출_없이_준비중_문구를_반환한다() {
        OpenAIClient client = mock(OpenAIClient.class);
        FactReportService service = new FactReportService(client, "gpt-4o-mini", 3L, "rule");

        String result = service.generate(SpendStatus.OVER, 950000L, 1000000L, "카페");

        assertThat(result).isEqualTo(FactReportService.FALLBACK_MESSAGE);
        verifyNoInteractions(client);
    }
}
