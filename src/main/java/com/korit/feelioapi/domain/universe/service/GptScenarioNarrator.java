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
            지금처럼 쓰는 미래(CURRENT)와, 소비가 가장 몰린 항목을 줄인 미래(REDUCED)를
            각각 코멘트 3개로 말해줘.

            가장 중요한 것: 두 미래는 역할이 다르다. 같은 말투로 쓰지 마라.
            나란히 놓고 비교하라고 만든 화면이라, 둘이 비슷하면 볼 이유가 없다.

            CURRENT — 짚어 준다.
              이대로 두면 목표가 얼마나 멀어지는지 사실대로 말하고, 무엇이 발목을 잡는지
              소비 항목 이름을 대서 지목하고, 그래서 무엇을 하면 되는지까지 말해라.
              기분 좋으라고 있는 칸이 아니다. 다만 비아냥대거나 사람을 탓하지는 마라.
              탓하는 대상은 사람이 아니라 지출 항목이다.

            REDUCED — 보상을 보여준다.
              줄이면 얼마나 당겨지는지, 매달 얼마가 더 남는지 구체적으로 말해라.
              "이렇게 하면 이만큼 빨라진다"가 읽혀야 한다. 격려하되 근거는 숫자로 댄다.
              REDUCED도 도달 불가라면 실패 판정부터 말하지 말고, 먼저 실제로 줄인 지출액을
              성과로 인정한 뒤 아직 매달 모이는 돈이 없고 추가 절약이 필요하다고 말해라.

            각 미래의 코멘트 3개는 이 순서로 이어져야 한다.
            CURRENT
              1번째: 이대로면 언제 닿는지. 한 달 미만이면 정확한 일수, 그 이상이면 개월 수를 넣고 팩폭한다. 괄호 안 일수는 쓰지 않는다
              2번째: 무엇 때문인지 — 가장 많이 쓴 항목 이름을 대서 지목한다
              3번째: 그래서 무엇을 하면 되는지 (줄일 항목과 방향)
            REDUCED
              1번째: 줄이면 언제 닿는지 한 달 미만이면 정확한 일수, 그 이상이면 개월 수를 넣고 단축 일수와 함께 먼저 칭찬한다. 괄호 안 일수는 쓰지 않는다
              2번째: 줄이면 매달 얼마가 더 남는지
              3번째: 그 돈이 남은 금액을 어떻게 앞당기는지

            절대 하지 말 것:
            - 목표가 어떤 경험인지·어떤 의미인지 말하지 마라.
              ("새로운 경험", "소중한 추억", "설렘을 더해요" 같은 말 금지)
            - 소비와 무관한 조언을 하지 마라. (마음가짐·취미·습관 일반론 금지)
            - 감정은 언급하지 마라.
            - 사용자가 화면에서 이미 보고 있는 숫자를 그대로 옮겨 적기만 하지 마라.
              문장은 숫자가 아니라 '그래서 무엇을 뜻하는지'를 말해야 한다.

            반드시 지킬 것:
            - 모든 문장은 반드시 존댓말(요/습니다)로 써라. 반말은 절대 쓰지 마라.
            - 목표 이름을 그대로 불러줘라. '목표'라고만 뭉뚱그리지 마라.
            - 줄일 소비 항목 이름을 그대로 불러줘라. 무엇을 줄이는지가 문장에 있어야 한다.
            - 숫자는 입력으로 주어진 값만 써라. 네가 계산하거나 다른 숫자를 지어내지 마라.
            - "도달 불가"라고 주어진 시나리오에는 개월 수를 쓰지 마라.
              CURRENT 라면 쓰는 돈이 버는 돈을 넘고 있다는 사실을 짚고,
              REDUCED 라면 줄이면 닿을 수 있다는 뜻을 담아라.
            - 각 코멘트는 60자 이내로.

            JSON 배열의 배열만 출력하고 다른 말은 붙이지 마라.
            바깥 배열은 반드시 2개, CURRENT 먼저 REDUCED 나중. 안쪽 배열은 각각 코멘트 3개.

            아래는 역할 차이를 보여주는 예시일 뿐이다. 숫자도 문장도 전부 입력에 맞게 새로 써라.
            예시 문장을 그대로 베끼지 마라.
            [["이대로 쓰면 유럽 여행까지 12개월 걸려요.","발목을 잡는 건 카페 지출이에요. 이번 달에 가장 많이 썼어요.","카페 지출을 줄이는 게 가장 빠른 길이에요."],
             ["이렇게 줄이면 9개월 뒤 도착, 3개월 빨라져요.","카페 지출을 줄이면 매달 100,000원이 더 남아요.","그 돈이 남은 3,600,000원을 앞당겨 줘요."]]
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
                               @Value("${openai.timeout-seconds-universe:10}") long timeoutSeconds,
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
                if (cleaned.stream().flatMap(List::stream).allMatch(this::looksLikeHonorificSentence)
                        && containsRequiredProjectionFacts(cleaned, context)) {
                    return cleaned;
                }
                log.warn("시나리오 문장에 필수 목표·기간 정보가 빠졌다. 규칙기반으로 대체한다. 응답={}", cleaned);
                return fallback.narrate(context);
            }
            // 원문을 남긴다. 형식이 왜 어긋났는지는 응답을 봐야만 알 수 있는데,
            // 예전에는 개수만 찍어서 매번 추측으로 시작해야 했다.
            log.warn("시나리오 문장 형식 불일치(기대 {}행, 실제 {}행). 규칙기반으로 대체한다. 파싱결과={}",
                    SCENARIO_COUNT, parsed.size(), parsed);
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
        input.append(String.format(Locale.KOREA, "해당 소비를 줄여 실제로 아낀 금액: 매달 %,d원%n", context.savedPerMonth()));
        input.append("CURRENT(지금처럼): ").append(describeDuration(context.currentMonths(), context.currentDays())).append('\n');
        input.append("REDUCED(줄이면): ").append(describeDuration(context.reducedMonths(), context.reducedDays())).append('\n');
        if (context.currentDays() != null && context.reducedDays() != null) {
            input.append("줄였을 때 단축되는 정확한 일수: ")
                    .append(Math.max(0, context.currentDays() - context.reducedDays())).append("일\n");
        }
        return input.toString();
    }

    private String describeDuration(Integer months, Integer days) {
        if (months == null) {
            return "도달 불가";
        }
        if (months == 0) {
            return "이미 목표 금액 달성";
        }
        return days != null && days < 30
                ? days + "일 뒤 도달"
                : months + "개월 뒤 도달 (단축 일수 계산값: " + days + "일)";
    }

    /** AI가 그럴듯한 빈말로 핵심 숫자를 빼면 계산이 보장된 폴백을 사용한다. */
    private boolean containsRequiredProjectionFacts(List<List<String>> rows, NarrationContext context) {
        String current = rows.get(0).get(0);
        String reduced = rows.get(1).get(0);
        String goal = context.goalName() == null ? "" : context.goalName().trim();
        boolean goalPresent = goal.isBlank() || current.contains(goal) && reduced.contains(goal);
        boolean currentDuration = context.currentMonths() == null
                ? current.contains("도달") || current.contains("넘")
                : containsDisplayedDuration(current, context.currentMonths(), context.currentDays());
        boolean reducedDuration = context.reducedMonths() == null
                ? reduced.contains("아꼈") || reduced.contains("줄")
                : containsDisplayedDuration(reduced, context.reducedMonths(), context.reducedDays());
        boolean noParenthesizedDays = !current.matches(".*\\(\\d+일\\).*")
                && !reduced.matches(".*\\(\\d+일\\).*");
        return goalPresent && currentDuration && reducedDuration && noParenthesizedDays;
    }

    private boolean containsDisplayedDuration(String sentence, Integer months, Integer days) {
        return days != null && days < 30
                ? sentence.contains(days + "일")
                : sentence.contains(months + "개월");
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

    private boolean looksLikeHonorificSentence(String value) {
        if (!looksLikeSentence(value)) {
            return false;
        }
        String sentence = value.trim().replaceFirst("[.!?]+$", "");
        return sentence.endsWith("요") || sentence.endsWith("니다") || sentence.endsWith("까요");
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
