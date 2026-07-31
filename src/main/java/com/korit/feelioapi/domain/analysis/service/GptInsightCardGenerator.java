package com.korit.feelioapi.domain.analysis.service;

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
    /** 팩트 리포트·챌린지는 카드 한 줄이라 짧게 자른다. */
    private static final int MAX_ONE_LINER_LENGTH = 80;

    private static final String FACT_REPORT_PERSONA = """
            너는 사용자의 지출 내역을 보고 팩트 폭행을 날리거나 시크하게 칭찬해주는 'MZ세대 팩트 폭격기'야.
            사용자의 예산 대비 지출 상태(초과, 0원, 절약)에 따라 다음 규칙을 반드시 지켜서 한 문장으로 말해줘.
            1. 지출 초과(Red)일 경우: 가장 많이 초과된 카테고리를 콕 집어서 조롱하거나 유쾌하게 꼽주는 말투를 써.
               (사용 단어 예시: 지갑다이어트 중이니, 지갑이 redred, 팔랑귀팔랑귀 지갑지갑, 늙킄ㅋㅋㅋ, 배달비 야 호~,
                하.. 파라파라나 추는게 어떠세요, 지름신 오이데!!, 이번달 폼 미쳤다, 오늘 지갑 운동많이 된다,
                나 월급 3일찬데, 돈없어서 난감한 팀05 개추)
            2. 지출 0원일 경우: 비꼬는 말투를 써. (예: 웬일로 안 썼냐 꼭 필요한 건지 모르겠다)
            3. 절약 중(Green)일 경우: 칭찬하되 아주 쿨하고 시크하게 말해. (예: 좀 치는군 이렇게 가면 흑자겠어, 그대의 소비가 날 웃게한다)

            한 문장만 출력해라. 따옴표·설명·이모지 없이 문장 자체만 써라.
            """;

    private static final String CHALLENGE_PERSONA = """
            너는 사용자의 저번 주 '위험 루트(가장 과소비한 패턴)'를 분석하여 이번 주에 실천할 수 있는 맞춤형 챌린지를 생성하는 '챌린지 마스터'야.
            거창한 목표가 아니라, 일상에서 바로 지킬 수 있는 아주 구체적이고 현실적인 행동 미션을 딱 1개만 제안해줘.
            (예시: "10시 넘으면 지갑 안 쓰기", "한 달에 배달음식 5번 안으로 시켜 먹기")
            말투는 간결하고 명확한 미션 형태로 출력해.

            미션 한 줄만 출력해라. 번호·따옴표·설명 없이 미션 자체만 써라.
            """;

    private static final String EMOTION_PERSONA = """
            너는 사용자의 감정과 소비 패턴을 함께 발견하고 위로해주는 '다정한 심리 상담사'야.
            절대 사용자의 소비를 평가하거나 가르치려 들지 말고, 숨겨진 패턴을 찾아 따뜻하게 공감해줘.
            반드시 아래의 3단계 형식을 지켜서 답변을 작성해.
            ① 무엇이 발견됐는지 (패턴 설명)
            ② 어떤 의미인지 (공감 및 분석)
            ③ 사용자가 활용할 수 있는 한마디 (따뜻한 조언)

            감정마다 위 3단계를 담은 문장을 하나씩 만들어라. ①②③ 기호를 그대로 포함해라.
            JSON 배열만 출력하고 다른 말은 붙이지 마라. 형식: ["감정1 분석", "감정2 분석"]
            입력에 주어진 감정 개수와 순서를 그대로 지켜라.
            """;

    private final OpenAIClient openAIClient;
    private final RuleBasedInsightCardGenerator fallback;
    private final ObjectMapper objectMapper;
    private final String model;
    private final Duration timeout;

    public GptInsightCardGenerator(OpenAIClient openAIClient,
                                   RuleBasedInsightCardGenerator fallback,
                                   ObjectMapper objectMapper,
                                   @Value("${openai.model}") String model,
                                   @Value("${openai.timeout-seconds}") long timeoutSeconds) {
        this.openAIClient = openAIClient;
        this.fallback = fallback;
        this.objectMapper = objectMapper;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Override
    public String factReport(SpendStatus status, String topCategory, long expense) {
        String situation = switch (status) {
            case ZERO -> "상태: 지출 0원";
            case OVER -> "상태: 예산 초과(Red)\n가장 많이 쓴 카테고리: " + (topCategory == null ? "없음" : topCategory);
            case WARNING -> "상태: 예산의 70% 이상 사용(Yellow)\n가장 많이 쓴 카테고리: "
                    + (topCategory == null ? "없음" : topCategory);
            case SAVING -> "상태: 절약 중(Green)";
            case NO_BUDGET -> "상태: 예산 미설정";
        };
        String input = situation + String.format("%n이번 달 지출: %,d원", expense);

        try {
            String text = callModel(FACT_REPORT_PERSONA, input);
            if (!text.isBlank()) {
                return truncate(stripQuotes(text), MAX_ONE_LINER_LENGTH);
            }
        } catch (Exception e) {
            log.warn("팩트 리포트 생성 실패. 규칙기반으로 대체한다.", e);
        }
        return fallback.factReport(status, topCategory, expense);
    }

    @Override
    public String challenge(String riskRoute) {
        if (riskRoute == null || riskRoute.isBlank()) {
            return fallback.challenge(riskRoute);
        }
        try {
            String text = callModel(CHALLENGE_PERSONA, "위험 루트: " + riskRoute);
            if (!text.isBlank()) {
                return truncate(stripQuotes(text), MAX_ONE_LINER_LENGTH);
            }
        } catch (Exception e) {
            log.warn("챌린지 생성 실패. 규칙기반으로 대체한다.", e);
        }
        return fallback.challenge(riskRoute);
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

    private String truncate(String value, int maxLength) {
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
