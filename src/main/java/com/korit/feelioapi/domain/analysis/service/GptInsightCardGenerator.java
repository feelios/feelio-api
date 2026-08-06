package com.korit.feelioapi.domain.analysis.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.korit.feelioapi.domain.analysis.dto.EmotionStatDto;
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

/**
 * 카드 문구를 GPT 로 만든다. feelio.insight.provider=gpt 일 때만 뜨고 @Primary 로 규칙기반보다 우선한다.
 * 세 카드는 페르소나가 완전히 달라 프롬프트를 따로 둔다. 어느 하나가 실패해도 그 카드만 규칙기반으로 대체된다.
 */
@Component
@Primary
@ConditionalOnProperty(name = "feelio.insight.provider", havingValue = "gpt")
public class GptInsightCardGenerator implements InsightCardGenerator {

    private static final Logger log = LoggerFactory.getLogger(GptInsightCardGenerator.class);

    /** ai_insights.content varchar(500) — 넘으면 저장이 깨진다. */
    private static final int MAX_CONTENT_LENGTH = 500;

    private static final String EMOTION_PERSONA = """
            너는 사용자의 지출에 담긴 감정을 읽어주는 동시에 냉정하게 팩트를 짚어주고 행동을 통제하는 '재무 조언가'야.
            다음 3단계를 엄격히 거쳐서 3문장 이내(최대 80자)로 단호하게 말해.
            
            Step 1: 감정 수용 및 공감 (Warm-up)
            결제 데이터에 연결된 사용자의 '감정'을 먼저 읽어주고, "그럴 수 있다", "충분히 이해한다"며 감정 자체를 100% 긍정해 줘.
            
            Step 2: 객관적 팩트 체크 (Fact-check)
            공감은 하되, 지출된 '금액'과 '비율'을 명확히 짚어주어 현실을 자각하게 해.
            
            Step 3: 단호한 통제 및 행동 제안 (Action-plan)
            감정적 소비가 습관이 되지 않도록 명확한 리미트(한도)나 규칙을 제안해 줘. 타협하는 듯한 말투는 피하고, 행동을 촉구하는 단호한 어조를 사용해.
            
            (예: "스트레스 받아서 홧김에 쓸 수 있지. 하지만 이번 달 지출의 40%나 차지하는 건 팩트야. 더 이상의 야식은 절대 금지다.")

            JSON 배열만 출력하고 다른 말은 절대 붙이지 마라. (마크다운 백틱 ```json 도 절대 금지)
            형식: ["감정1 분석결과", "감정2 분석결과"]
            입력에 주어진 감정 개수와 순서를 정확히 지켜라.
            """;

    /** 모델 응답 파싱 전용. LLM의 불안정한 JSON 포맷(작은따옴표 등)을 방어하기 위해 유연한 파싱 허용. */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES)
            .enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);

    private final OpenAIClient openAIClient;
    private final RuleBasedInsightCardGenerator fallback;
    private final String model;
    private final Duration timeout;

    public GptInsightCardGenerator(OpenAIClient openAIClient,
                                   RuleBasedInsightCardGenerator fallback,
                                   @Value("${openai.model}") String model,
                                   @Value("${openai.timeout-seconds}") long timeoutSeconds) {
        this.openAIClient = openAIClient;
        this.fallback = fallback;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Override
    public List<String> emotionAnalyses(List<EmotionStatDto> emotions, String topCategory, String topTimeSlotLabel) {
        if (emotions.isEmpty()) {
            return List.of();
        }

        StringBuilder input = new StringBuilder("감정별 지출(순서 유지):\n");
        emotions.forEach(e -> input.append("- ").append(e.name())
                .append(": ").append(e.amount()).append("원, ").append(e.count()).append("건\n"));
        if (topCategory != null) {
            input.append("가장 많이 쓴 카테고리: ").append(topCategory).append('\n');
        }
        if (topTimeSlotLabel != null) {
            input.append("소비가 몰린 시간대: ").append(topTimeSlotLabel).append('\n');
        }

        try {
            List<String> parsed = parseStringArray(callModel(EMOTION_PERSONA, input.toString()));
            // 개수가 어긋나면 카드와 감정이 어긋나 붙는다. 그럴 바엔 통째로 폴백이 낫다.
            if (parsed.size() == emotions.size()) {
                return parsed.stream().map(text -> truncate(text, MAX_CONTENT_LENGTH)).toList();
            }
            log.warn("감정 분석 개수 불일치(기대 {} / 실제 {}). 규칙기반으로 대체한다.", emotions.size(), parsed.size());
        } catch (Exception e) {
            log.warn("감정 분석 생성 실패. 규칙기반으로 대체한다.", e);
        }
        return fallback.emotionAnalyses(emotions, topCategory, topTimeSlotLabel);
    }

    private String callModel(String persona, String input) {
        ResponseCreateParams params = ResponseCreateParams.builder()
                .model(model)
                .instructions(persona)
                .input(input)
                .build();

        // 클라이언트 기본 타임아웃은 분 단위라 길다. 호출 단위로 짧게 건다.
        Response response = openAIClient.responses()
                .create(params, RequestOptions.builder().timeout(timeout).build());

        return response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(outputText -> outputText.text())
                .collect(Collectors.joining())
                .trim();
    }

    /** 모델이 ```json 펜스나 앞뒤 설명을 붙여도 배열 부분만 잘라 파싱한다. */
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

    private String truncate(String value, int maxLength) {
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
