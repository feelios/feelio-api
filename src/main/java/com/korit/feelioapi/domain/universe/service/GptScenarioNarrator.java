package com.korit.feelioapi.domain.universe.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import com.korit.feelioapi.global.ai.AiCallGuard;

/**
 * 시나리오 문장을 GPT 로 만든다. feelio.insight.provider=gpt 일 때만 뜨고 @Primary 로 규칙기반보다 우선한다.
 * 두 문장을 한 번의 호출로 받는다 — 호출을 나누면 지연·비용이 두 배인데 두 문장은 서로를 참조해야 자연스럽다.
 */
@Component
@Primary
@ConditionalOnProperty(name = "feelio.insight.provider", havingValue = "gpt")
public class GptScenarioNarrator implements ScenarioNarrator {

    private static final Logger log = LoggerFactory.getLogger(GptScenarioNarrator.class);

    /** 카드 한 줄이라 짧게 자른다. */
    private static final int MAX_NARRATION_LENGTH = 120;
    /** 계약 §9 상 시나리오는 CURRENT·REDUCED 2건 고정. */
    private static final int SCENARIO_COUNT = 2;

    private static final String PERSONA = """
            너는 사용자의 저축 목표 달성 시점을 두 가지 미래로 보여주는 '평행우주 안내자'야.
            사용자가 지금처럼 쓰는 미래(CURRENT)와, 특정 감정 소비를 줄인 미래(REDUCED)를 각각 한 문장으로 말해줘.
            말투는 담백하고 다정하게. 다그치거나 훈계하지 마라.

            반드시 지킬 것:
            - 개월 수는 입력으로 주어진 값만 써라. 네가 계산하거나 다른 숫자를 지어내지 마라.
            - "도달 불가"라고 주어진 시나리오에는 개월 수를 쓰지 말고, 조금 줄여보자는 뜻만 담아라.
            - REDUCED 문장은 CURRENT 보다 얼마나 빨라지는지가 드러나면 좋다.
            - 각 문장은 60자 이내로.

            JSON 배열만 출력하고 다른 말은 붙이지 마라. 형식: ["CURRENT 문장", "REDUCED 문장"]
            반드시 2개, CURRENT 먼저 REDUCED 나중 순서를 지켜라.
            """;

    /** 모델 응답 파싱 전용. 컨테이너에 ObjectMapper 빈이 없어 직접 만든다. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OpenAIClient openAIClient;
    private final RuleBasedScenarioNarrator fallback;
    private final String model;
    private final Duration timeout;

    private final AiCallGuard guard;

    public GptScenarioNarrator(OpenAIClient openAIClient,
                               RuleBasedScenarioNarrator fallback,
                               @Value("${openai.model}") String model,
                               @Value("${openai.timeout-seconds}") long timeoutSeconds,
                               AiCallGuard guard) {
        this.openAIClient = openAIClient;
        this.fallback = fallback;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.guard = guard;
    }

    @Override
    public List<List<String>> narrate(NarrationContext context) {
        try {
            List<List<String>> parsed = parseStringMatrix(callModel(buildInput(context)));
            // 개수가 어긋나면 문장이 엉뚱한 시나리오에 붙는다. 그럴 바엔 통째로 폴백이 낫다.
            if (parsed.size() == SCENARIO_COUNT) {
                return parsed.stream().map(row -> row.stream().map(text -> truncate(stripQuotes(text))).toList()).toList();
            }
            log.warn("시나리오 문장 개수 불일치(기대 {} / 실제 {}). 규칙기반으로 대체한다.",
                    SCENARIO_COUNT, parsed.size());
        } catch (Exception e) {
            log.warn("시나리오 문장 생성 실패. 규칙기반으로 대체한다.", e);
        }
        return fallback.narrate(context);
    }

    private String buildInput(NarrationContext context) {
        StringBuilder input = new StringBuilder();
        input.append("목표: ").append(context.goalName()).append('\n');
        input.append("줄일 감정 소비: ")
                .append(context.focusEmotionName() == null ? "특정 감정 없음" : context.focusEmotionName())
                .append('\n');
        input.append("CURRENT(지금처럼): ").append(describeMonths(context.currentMonths())).append('\n');
        input.append("REDUCED(줄이면): ").append(describeMonths(context.reducedMonths())).append('\n');
        return input.toString();
    }

    private String describeMonths(Integer months) {
        if (months == null) {
            return "도달 불가";
        }
        return months == 0 ? "이미 목표 금액 달성" : months + "개월 뒤 도달";
    }

    private String callModel(String input) {
        ResponseCreateParams params = ResponseCreateParams.builder()
                .model(model)
                .instructions(PERSONA)
                .input(input)
                .build();

        // 클라이언트 기본 타임아웃은 분 단위라 길다. 호출 단위로 짧게 건다.
        Response response = guard.call("평행우주 서술", () -> openAIClient.responses()
                .create(params, RequestOptions.builder().timeout(timeout).build()));

        return response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(outputText -> outputText.text())
                .collect(Collectors.joining())
                .trim();
    }

    /** 모델이 ```json 펜스나 앞뒤 설명을 붙여도 배열 부분만 잘라 파싱한다. */
    private List<List<String>> parseStringMatrix(String rawText) throws Exception {
        int start = rawText.indexOf('[');
        int end = rawText.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return List.of();
        }
        JsonNode array = objectMapper.readTree(rawText.substring(start, end + 1));
        if (!array.isArray()) {
            return List.of();
        }
        List<List<String>> matrix = new ArrayList<>();
        for (JsonNode node : array) {
            if (node.isArray()) {
                List<String> row = new ArrayList<>();
                for (JsonNode child : node) {
                    String text = child.asText("").trim();
                    if (!text.isBlank()) {
                        row.add(text);
                    }
                }
                matrix.add(row);
            }
        }
        return matrix;
    }
    
    private List<String> parseStringArray(String rawText) throws Exception {
        int start = rawText.indexOf('[');
        int end = rawText.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return List.of();
        }
        JsonNode array = objectMapper.readTree(rawText.substring(start, end + 1));
        if (!array.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode node : array) {
            String text = node.asText("").trim();
            if (!text.isBlank()) {
                values.add(text);
            }
        }
        return values;
    }

    /** 모델이 문장을 따옴표로 감싸는 경우가 잦다. 카드에 그대로 노출되면 어색하다. */
    private String stripQuotes(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2
                && (trimmed.startsWith("\"") && trimmed.endsWith("\"")
                || trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private String truncate(String value) {
        String trimmed = value.trim();
        return trimmed.length() <= MAX_NARRATION_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_NARRATION_LENGTH);
    }
}
