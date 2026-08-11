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
    /** 화면이 한 시나리오 안에서 돌려 보여주는 코멘트 수. 규칙기반 폴백과 같은 값으로 맞춘다. */
    private static final int NARRATIONS_PER_SCENARIO = 3;

    private static final String PERSONA = """
            너는 사용자의 저축 목표 달성 시점을 두 가지 미래로 보여주는 '평행우주 안내자'야.
            사용자가 지금처럼 쓰는 미래(CURRENT)와, 소비가 가장 몰린 카테고리 지출을 줄인
            미래(REDUCED)를 각각 코멘트 3개로 말해줘.

            화면은 이 3개를 차례로 돌려 보여준다. 같은 말을 바꿔 쓰면 돌리는 의미가 없으니
            세 개가 서로 다른 각도여야 한다.
            1번째: 언제 목표에 닿는지 (개월 수를 넣는다)
            2번째: 그 목표가 사용자에게 어떤 의미일지
            3번째: 다음 한 걸음으로 해볼 만한 것

            말투는 담백하고 다정하게. 다그치거나 훈계하지 마라.

            반드시 지킬 것:
            - 목표 이름을 그대로 불러줘라. '목표'라고만 뭉뚱그리지 마라.
            - REDUCED 는 줄일 소비 항목 이름을 그대로 불러줘라. 무엇을 줄이는지가 문장에 있어야 한다.
            - 감정은 언급하지 마라. 이 화면은 소비 항목만 다룬다.
            - 개월 수는 입력으로 주어진 값만 써라. 네가 계산하거나 다른 숫자를 지어내지 마라.
            - "도달 불가"라고 주어진 시나리오에는 개월 수를 쓰지 말고, 조금 줄여보자는 뜻만 담아라.
            - REDUCED 의 1번째 코멘트는 CURRENT 보다 얼마나 빨라지는지가 드러나면 좋다.
            - 각 코멘트는 60자 이내로.

            JSON 배열의 배열만 출력하고 다른 말은 붙이지 마라.
            형식: [["CURRENT 1","CURRENT 2","CURRENT 3"],["REDUCED 1","REDUCED 2","REDUCED 3"]]
            바깥 배열은 반드시 2개, CURRENT 먼저 REDUCED 나중. 안쪽 배열은 각각 코멘트 3개.
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
            // 빈 행도 막는다 — 코멘트가 하나도 없는 시나리오는 화면에서 빈 칸이 된다.
            if (parsed.size() == SCENARIO_COUNT && parsed.stream().noneMatch(List::isEmpty)) {
                return parsed.stream()
                        .map(row -> row.stream()
                                .limit(NARRATIONS_PER_SCENARIO)
                                .map(text -> truncate(stripQuotes(text)))
                                .toList())
                        .toList();
            }
            log.warn("시나리오 문장 형식 불일치(기대 {}행, 실제 {}행). 규칙기반으로 대체한다.",
                    SCENARIO_COUNT, parsed.size());
        } catch (Exception e) {
            log.warn("시나리오 문장 생성 실패. 규칙기반으로 대체한다.", e);
        }
        return fallback.narrate(context);
    }

    private String buildInput(NarrationContext context) {
        StringBuilder input = new StringBuilder();
        input.append("목표: ").append(context.goalName()).append('\n');
        input.append("줄일 소비 항목: ")
                .append(context.focusCategoryName() == null ? "특정 항목 없음" : context.focusCategoryName())
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
