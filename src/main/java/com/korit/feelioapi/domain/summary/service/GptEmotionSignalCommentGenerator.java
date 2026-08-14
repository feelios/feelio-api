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
    private static final int MAX_LENGTH = 65;

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

                    가장 의미 있는 변화 하나를 골라, 사용자가 자기 소비를 따뜻하게 돌아볼 수 있는 짧은 존댓말 문장 하나를 써라.
                    감정명과 입력에 있는 증감 방향을 반드시 반영하되, 원인이나 심리를 추측하지 마라.
                    숫자는 입력값만 사용할 수 있고 새로 계산하거나 지어내면 안 된다.
                    비난·훈계·과장·이모지·따옴표 없이 55자 이내 한 문장만 출력해라.
                    감정명은 소비 기록에 붙인 태그이지 사용자의 성격이나 현재 상태가 아니다.
                    감정 이름에서 장면을 연상하지 마라. 무덤덤을 조용함으로, 평온을 휴식으로 해석하면 안 된다.
                    음미하다·선물·위로·휴식·고요·조용한 순간 같은 시적인 표현은 쓰지 마라.
                    관찰한 변화 뒤에 쉼표를 쓰고 "소비할 때 마음이 어떻게 달라졌는지 살펴보세요"처럼
                    소비 당시의 마음을 돌아보게 하는 짧고 구체적인 말을 덧붙여라.
                    증감률과 건수는 같은 사실을 반복하므로 둘 중 하나만 문장에 사용해라.
                    좋은 예: "무덤덤 소비가 지난달보다 줄었어요, 소비할 때 마음이 어떻게 달라졌는지 살펴보세요."
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
            if (containsPoeticGuess(text)) return null;
            return text.length() <= MAX_LENGTH ? text : null;
        } catch (Exception e) {
            log.warn("홈 감정 신호 생성 실패. 규칙기반으로 대체한다.", e);
            return null;
        }
    }

    private boolean containsPoeticGuess(String text) {
        return List.of("음미", "선물", "위로", "휴식", "고요", "조용한 순간")
                .stream()
                .anyMatch(text::contains);
    }
}
