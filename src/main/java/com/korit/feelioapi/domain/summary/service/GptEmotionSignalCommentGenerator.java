package com.korit.feelioapi.domain.summary.service;

import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.korit.feelioapi.global.ai.AiCallGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
@Primary
@ConditionalOnProperty(name = "feelio.insight.provider", havingValue = "gpt")
public class GptEmotionSignalCommentGenerator implements EmotionSignalCommentGenerator {
    private static final Logger log = LoggerFactory.getLogger(GptEmotionSignalCommentGenerator.class);
    private static final int MAX_LENGTH = 90;

    private final OpenAIClient client;
    private final String model;
    private final Duration timeout;
    private final AiCallGuard guard;

    public GptEmotionSignalCommentGenerator(OpenAIClient client,
                                            @Value("${openai.model}") String model,
                                            @Value("${openai.timeout-seconds}") long timeoutSeconds,
                                            AiCallGuard guard) {
        this.client = client;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.guard = guard;
    }

    @Override
    public String generate(int year, int month, List<EmotionSignal> signals) {
        if (signals == null || signals.isEmpty()) return null;
        try {
            String facts = signals.stream().map(signal -> String.format(Locale.KOREA,
                    "- %s: 지난달 %d건, 이번 달 %d건, 증감률 %+d%%, 이번 달 관련 지출 %,d원",
                    signal.name(), signal.previousCount(), signal.currentCount(), signal.rate(), signal.currentAmount()))
                    .collect(Collectors.joining("\n"));
            String prompt = """
                    너는 감정 소비 기록 앱의 다정하지만 솔직한 AI 분석가다.
                    아래는 %d년 %d월 사용자의 실제 감정별 소비 변화다.
                    %s

                    가장 의미 있는 변화 하나를 골라 사용자가 자기 소비를 돌아볼 수 있는 반말 문장 하나를 써라.
                    감정명과 입력에 있는 증감 방향을 반드시 반영하되, 원인이나 심리를 추측하지 마라.
                    숫자는 입력값만 사용할 수 있고 새로 계산하거나 지어내면 안 된다.
                    비난·훈계·과장·이모지·따옴표 없이 70자 이내 한 문장만 출력해라.
                    "괜찮아"처럼 무조건 달래기보다 관찰한 변화와 다음 확인 행동을 자연스럽게 연결해라.
                    """.formatted(year, month, facts);
            Response response = guard.call("홈 감정 신호", () -> client.responses().create(
                    ResponseCreateParams.builder().model(model).input(prompt).build(),
                    RequestOptions.builder().timeout(timeout).build()));
            String text = response.output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream())
                    .flatMap(content -> content.outputText().stream())
                    .map(output -> output.text()).collect(Collectors.joining()).trim();
            if (text.length() < 6 || text.codePoints().noneMatch(c -> c >= 0xAC00 && c <= 0xD7A3)) return null;
            return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH);
        } catch (Exception e) {
            log.warn("홈 감정 신호 생성 실패. 규칙기반으로 대체한다.", e);
            return null;
        }
    }
}
