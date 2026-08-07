package com.korit.feelioapi.domain.summary.service;

import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.stream.Collectors;
import com.korit.feelioapi.global.ai.AiCallGuard;

@Component
@ConditionalOnProperty(name = "feelio.insight.provider", havingValue = "gpt")
public class GptSummaryAiCommentGenerator implements SummaryAiCommentGenerator {

    private static final Logger log = LoggerFactory.getLogger(GptSummaryAiCommentGenerator.class);
    private static final int MAX_LENGTH = 180;

    private final OpenAIClient openAIClient;
    private final String model;
    private final Duration timeout;

    private final AiCallGuard guard;

    public GptSummaryAiCommentGenerator(OpenAIClient openAIClient,
                                        @Value("${openai.model}") String model,
                                        @Value("${openai.timeout-seconds}") long timeoutSeconds,
                                        AiCallGuard guard) {
        this.openAIClient = openAIClient;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.guard = guard;
    }

    @Override
    public String generate(int year, int month, long currentExpense, long previousExpense) {
        try {
            String prompt = String.format(
                    "%d년 %d월 현재 지출은 %,d원이고 전월 지출은 %,d원이다. "
                            + "두 금액을 비교해 소비 총평을 따뜻하고 중립적인 한국어 한 문장으로 써라. "
                            + "훈계하거나 과장하지 말고 180자 이내 문장만 출력하라.",
                    year, month, currentExpense, previousExpense);
            ResponseCreateParams params = ResponseCreateParams.builder().model(model).input(prompt).build();
            RequestOptions options = RequestOptions.builder().timeout(timeout).build();
            Response response = guard.call("월간 총평", () -> openAIClient.responses().create(params, options));
            String text = response.output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream())
                    .flatMap(content -> content.outputText().stream())
                    .map(output -> output.text())
                    .collect(Collectors.joining())
                    .trim();
            if (text.isBlank()) {
                return null;
            }
            return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH);
        } catch (Exception e) {
            log.warn("홈 AI 멘트 생성 실패({}-{}). 빈 멘트로 응답한다.", year, month, e);
            return null;
        }
    }
}
