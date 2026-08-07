package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.EmotionStatDto;
import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import com.korit.feelioapi.global.ai.AiCallGuard;

/** 당월 감정별 소비를 발견·의미·조언의 3단계로 풀어내는 심리 상담사 서비스. */
@Service
public class EmotionAnalysisService {

    public static final String FALLBACK_MESSAGE = "감정 소비 분석을 준비 중이에요.";

    private static final Logger log = LoggerFactory.getLogger(EmotionAnalysisService.class);
    private static final int MAX_LENGTH = 500;
    private static final String PERSONA = """
            너는 소비를 평가하거나 가르치지 않고 감정과 소비의 연결을 따뜻하게 짚어주는 심리 상담사다.
            제공된 감정별 지출과 대표 소비 맥락만 사용해 아래 세 단계를 정확히 한 번씩 작성해라.
            ① 발견: 어떤 감정에서 소비가 두드러졌는지 구체적으로 설명한다.
            ② 의미: 그 패턴이 가질 수 있는 의미를 단정하지 않고 공감하며 해석한다.
            ③ 조언: 사용자가 다음 소비 전에 시도할 수 있는 작고 따뜻한 행동 하나를 제안한다.
            진단명·질환·중독을 단정하지 말고, 죄책감을 유발하거나 훈계하지 마라.
            반드시 "① 발견: ... ② 의미: ... ③ 조언: ..." 형식의 한국어 한 문장만 출력해라.
            """;

    private final OpenAIClient openAIClient;
    private final String model;
    private final Duration timeout;
    private final String provider;

    private final AiCallGuard guard;

    public EmotionAnalysisService(OpenAIClient openAIClient,
                                  @Value("${openai.model}") String model,
                                  @Value("${openai.timeout-seconds}") long timeoutSeconds,
                                  @Value("${feelio.insight.provider:rule}") String provider,
                                  AiCallGuard guard) {
        this.openAIClient = openAIClient;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.provider = provider;
        this.guard = guard;
    }

    public String generate(List<EmotionStatDto> emotions, String topCategory, String topTimeSlot) {
        if (emotions == null || emotions.isEmpty() || !"gpt".equalsIgnoreCase(provider)) {
            return FALLBACK_MESSAGE;
        }

        try {
            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(model)
                    .instructions(PERSONA)
                    .input(buildInput(emotions, topCategory, topTimeSlot))
                    .build();
            Response response = guard.call("감정 분석", () -> openAIClient.responses().create(
                    params,
                    RequestOptions.builder().timeout(timeout).build()
            ));
            String text = response.output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream())
                    .flatMap(content -> content.outputText().stream())
                    .map(output -> output.text())
                    .collect(Collectors.joining())
                    .trim();
            if (!hasThreeSteps(text)) {
                log.warn("감정 소비 분석이 3단계 형식을 지키지 않아 준비 중 문구로 대체한다.");
                return FALLBACK_MESSAGE;
            }
            return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH);
        } catch (Exception e) {
            log.warn("감정 소비 분석 생성 실패. 준비 중 문구로 대체한다.", e);
            return FALLBACK_MESSAGE;
        }
    }

    private String buildInput(List<EmotionStatDto> emotions, String topCategory, String topTimeSlot) {
        StringBuilder input = new StringBuilder("이번 달 감정별 지출(금액 내림차순):\n");
        emotions.stream().limit(3).forEach(emotion -> input
                .append("- ").append(emotion.name())
                .append(": ").append(emotion.amount()).append("원, ")
                .append(emotion.count()).append("건\n"));
        input.append("가장 많이 쓴 카테고리: ").append(topCategory == null ? "없음" : topCategory).append('\n');
        input.append("소비가 몰린 시간대: ").append(topTimeSlot == null ? "없음" : topTimeSlot);
        return input.toString();
    }

    private boolean hasThreeSteps(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        int discovery = text.indexOf("① 발견:");
        int meaning = text.indexOf("② 의미:");
        int advice = text.indexOf("③ 조언:");
        return discovery >= 0 && discovery < meaning && meaning < advice;
    }

    public String generatePattern(String emotion, String category, String timeSlot, int count) {
        if (!"gpt".equalsIgnoreCase(provider)) {
            return "[" + timeSlot + "] 시간대에 '" + emotion + "' 감정으로 '" + category + "' 지출이 " + count + "건 집중되는 패턴이 감지되었어요. 무의식적인 소비일 수 있으니 주의해 보세요!";
        }
        try {
            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(model)
                    .instructions("당신은 사용자의 소비 패턴을 날카롭게 분석하는 AI입니다. 1문장으로 짧고 뼈때리는 조언을 해주세요.")
                    .input("감정: " + emotion + ", 카테고리: " + category + ", 시간대: " + timeSlot + ", 반복횟수: " + count)
                    .build();
            Response response = guard.call("패턴 분석",
                    () -> openAIClient.responses().create(params, RequestOptions.builder().timeout(timeout).build()));
            String text = response.output().stream().flatMap(i -> i.message().stream()).flatMap(m -> m.content().stream()).flatMap(c -> c.outputText().stream()).map(o -> o.text()).collect(Collectors.joining()).trim();
            return text;
        } catch (Exception e) {
            return "패턴 분석 중 오류가 발생했습니다.";
        }
    }
}
