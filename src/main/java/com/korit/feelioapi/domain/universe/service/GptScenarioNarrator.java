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
import java.util.Locale;
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
    /** 이보다 짧으면 문장이 아니라 자리표시자나 부스러기로 본다. */
    private static final int MIN_NARRATION_LENGTH = 6;
    /** 계약 §9 상 시나리오는 CURRENT·REDUCED 2건 고정. */
    private static final int SCENARIO_COUNT = 2;
    /** 화면이 한 시나리오 안에서 돌려 보여주는 코멘트 수. 규칙기반 폴백과 같은 값으로 맞춘다. */
    private static final int NARRATIONS_PER_SCENARIO = 3;

    private static final String PERSONA = """
            너는 사용자의 저축 목표 달성 시점을 두 가지 미래로 보여주는 '평행우주 안내자'야.
            사용자가 지금처럼 쓰는 미래(CURRENT)와, 소비가 가장 몰린 카테고리 지출을 줄인
            미래(REDUCED)를 각각 코멘트 3개로 말해줘.

            이 화면은 소비와 목표 도달 시점만 다룬다. 세 코멘트 모두 돈 이야기여야 한다.

            중요: 카드에는 이미 "이번 달 지출 금액"과 "도달 개월 수"가 크게 적혀 있다.
            그 두 숫자를 그대로 되풀이하는 코멘트는 사용자가 이미 본 것을 다시 읽는 셈이라
            넘길 이유가 없다. 1번째 코멘트에서만 개월 수를 쓰고, 나머지는 카드에 없는 숫자
            (남은 금액, 매달 모으는 금액)로 말해라.

            화면은 3개를 차례로 돌려 보여주니 같은 말을 바꿔 쓰지 말고 근거를 하나씩 옮겨라.
            1번째: 언제 목표에 닿는지 (개월 수를 넣는다)
            2번째: 목표까지 남은 금액
            3번째: CURRENT 는 지금 매달 모으는 금액,
                   REDUCED 는 줄였을 때 매달 모으게 되는 금액

            말투는 담백하고 다정하게. 다그치거나 훈계하지 마라.

            절대 하지 말 것:
            - 목표가 어떤 경험인지·어떤 의미인지 말하지 마라.
              ("새로운 경험", "소중한 추억", "설렘을 더해요" 같은 말 금지)
            - 소비와 무관한 조언을 하지 마라. (마음가짐·취미·습관 일반론 금지)
            - 감정은 언급하지 마라.

            반드시 지킬 것:
            - 목표 이름을 그대로 불러줘라. '목표'라고만 뭉뚱그리지 마라.
            - REDUCED 는 줄일 소비 항목 이름을 그대로 불러줘라. 무엇을 줄이는지가 문장에 있어야 한다.
            - 숫자는 입력으로 주어진 값만 써라. 네가 계산하거나 다른 숫자를 지어내지 마라.
            - "도달 불가"라고 주어진 시나리오에는 개월 수를 쓰지 말고, 조금 줄여보자는 뜻만 담아라.
            - 각 코멘트는 60자 이내로.

            JSON 배열의 배열만 출력하고 다른 말은 붙이지 마라.
            바깥 배열은 반드시 2개, CURRENT 먼저 REDUCED 나중. 안쪽 배열은 각각 코멘트 3개.

            아래는 모양을 보여주는 예시일 뿐이다. 숫자도 문장도 전부 입력에 맞게 새로 써라.
            예시 문장을 그대로 베끼지 마라.
            [["지금 속도라면 약 12개월 뒤 유럽 여행에 닿아요.","이번 달 지출 250,000원 기준이에요.","이 속도면 내년 여름에 도착해요."],
             ["카페 지출을 줄이면 약 9개월, 3개월 빨라져요.","줄이면 매달 60,000원이 더 남아요.","그만큼 유럽 여행 도착이 앞당겨져요."]]
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
                List<List<String>> cleaned = parsed.stream()
                        .map(row -> row.stream()
                                .limit(NARRATIONS_PER_SCENARIO)
                                .map(text -> truncate(stripQuotes(text)))
                                .toList())
                        .toList();
                if (cleaned.stream().flatMap(List::stream).allMatch(this::looksLikeSentence)) {
                    return cleaned;
                }
                log.warn("시나리오 문장에 사람 말이 아닌 값이 섞였다. 규칙기반으로 대체한다. 응답={}", cleaned);
                return fallback.narrate(context);
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
        input.append(String.format(Locale.KOREA, "이번 달 지출(카드에 이미 표시됨): %,d원%n", context.monthlyExpense()));
        input.append(String.format(Locale.KOREA, "목표까지 남은 금액: %,d원%n", context.remaining()));
        input.append(String.format(Locale.KOREA, "지금 매달 모으는 금액: %,d원%n", context.currentSaving()));
        input.append(String.format(Locale.KOREA, "줄이면 매달 모으게 되는 금액: %,d원%n", context.reducedSaving()));
        input.append(String.format(Locale.KOREA, "줄이면 매달 더 남는 금액: %,d원%n", context.savedPerMonth()));
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
    

    /**
     * 사람에게 보여줄 문장인지 최소한만 본다.
     *
     * 프롬프트에 형식 예시를 `[["CURRENT 1", ...]]` 로 적었더니 모델이 그 자리표시자를
     * 그대로 돌려줬고, 카드에 "CURRENT 1" 이 그대로 떴다. 형식은 맞아서 파싱·개수 검사를
     * 전부 통과했다. 프롬프트만 고치면 같은 사고가 다른 모양으로 또 난다.
     *
     * 한글이 한 글자도 없거나 지나치게 짧으면 문장이 아니라고 본다. 자리표시자·코드·빈말이
     * 여기서 걸린다. 판단은 최소로 둔다 — 과하게 막으면 멀쩡한 문장까지 폴백된다.
     */
    // 테스트에서 직접 부를 수 있게 패키지 범위로 둔다. OpenAI 클라이언트를 흉내 내지 않고도 이 판단만 검증한다.
    boolean looksLikeSentence(String value) {
        return value != null
                && value.length() >= MIN_NARRATION_LENGTH
                && value.codePoints().anyMatch(c -> c >= 0xAC00 && c <= 0xD7A3);
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
