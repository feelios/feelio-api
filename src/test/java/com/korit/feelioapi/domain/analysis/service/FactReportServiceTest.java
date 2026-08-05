package com.korit.feelioapi.domain.analysis.service;

import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class FactReportServiceTest {

    @Test
    void GPT가_비활성화되면_외부호출_없이_규칙기반_문구를_반환한다() {
        OpenAIClient client = mock(OpenAIClient.class);
        RuleBasedInsightCardGenerator fallback = new RuleBasedInsightCardGenerator();
        FactReportService service = new FactReportService(client, fallback, "gpt-4o-mini", 3L, "rule");

        String result = service.generate(SpendStatus.SAVING, 100000L, 200000L, "식비");

        // 폴백 문구는 RuleBasedInsightCardGenerator 가 단일 기준이다. 문자열을 두 곳에 적어두면 어긋난다.
        assertThat(result).isEqualTo(fallback.factReport(SpendStatus.SAVING, "식비", 100000L));
        verifyNoInteractions(client);
    }
}
