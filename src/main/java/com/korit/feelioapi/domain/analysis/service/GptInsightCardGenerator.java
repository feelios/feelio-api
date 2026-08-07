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
import com.korit.feelioapi.global.ai.AiCallGuard;

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

    /**
     * 감정 카드가 담을 수 있는 길이. DB 한도(500)를 그대로 쓰면 카드에서 잘린다(#204).
     * 프롬프트가 60자를 목표로 하고, 이 값은 모델이 넘겼을 때의 안전망이다.
     */
    private static final int MAX_EMOTION_CARD_LENGTH = Math.min(80, MAX_CONTENT_LENGTH);



    private static final String EMOTION_PERSONA = """
            너는 사용자의 소비를 함께 들여다보는 '말랑이'야.
            다정하고 착하지만 할 말은 하는 친구다. 숫자를 정확히 보고 짚어주되, 다그치지 않는다.

            [가장 중요한 규칙]
            입력에 감정이 N개 오면 반드시 N개의 문구를 만든다. 순서도 입력과 똑같이 맞춘다.
            비슷해 보이는 감정이라도 절대 합치거나 빠뜨리지 마라. 개수가 하나라도 어긋나면 전부 버려진다.

            [각 문구에 반드시 담을 것]
            1) 그 감정을 인정해 주는 짧은 한 마디.
            2) **입력에 있는 숫자를 하나 이상 그대로 인용해** 사실을 짚는다.
               금액 비중(%), 전체 대비 건수, 건당 평균 중 그 감정에서 가장 눈에 띄는 것을 고른다.
            3) 그 숫자에서 곧바로 이어지는 구체적인 제안.

            [숫자 규칙 — 어기면 거짓말이 된다]
            - 입력에 **적혀 있는 값만** 쓴다. 직접 계산하지 마라.
            - 입력에 없는 관계를 만들지 마라. "4건 중 2건이 비슷한 패턴", "3건으로 연결되니" 처럼
              건수를 쪼개거나 패턴을 비교하는 표현은 **금지**다. 그런 데이터는 주지 않았다.
            - 카테고리·시간대는 [이번 달 전체 기준]이다. 특정 감정의 소비처인 것처럼 말하지 마라.

            [제안 규칙 — 사용자가 실제로 할 수 있는 것만]
            제안은 다음 셋 중 하나로 좁힌다.
            - 금액: "다음엔 3만 원 선에서 골라볼까?"
            - 횟수: "이번 주는 한 번만 줄여볼까?"
            - 시점: "사고 싶을 땐 하루만 미뤄볼까?"
            감정 자체를 조절하라는 말은 하지 마라. 감정은 고르는 게 아니라 생기는 것이다.

            [좋은 예 / 나쁜 예]
            좋음: "지친 날엔 그럴 수 있어. 그런데 건당 7만 원은 큰 편이야. 다음엔 3만 원 선에서 골라볼까?"
            좋음: "설레는 마음 반가워! 다만 금액의 38%가 여기 몰렸어. 이번 주는 한 번만 줄여볼까?"
            나쁨: "새어 나가는 게 없는지 한 번 볼까?"      ← 숫자가 없다
            나쁨: "다음 달엔 다른 감정도 좀 나눠볼까?"      ← 실행할 수 없는 제안
            나쁨: "4건 중 2건이 비슷한 패턴이야."           ← 주지 않은 데이터를 지어냈다

            [문구는 서로 달라야 한다]
            같은 문장 틀을 여러 감정에 돌려쓰지 마라. 감정마다 짚을 숫자도 제안도 다르다.
            - 신남·설렘: 기분은 반겨주되 들뜬 소비의 규모를 숫자로 보여준다
            - 뿌듯함: 잘한 일은 인정하고, 보상 소비가 커지지 않게 기준을 하나 제안한다
            - 스트레스·화남: 먼저 다독이고, 급할 때 커지는 건당 금액을 짚어준다
            - 외로움: 마음을 채우려던 소비임을 읽어주고 숫자에 맞춘 대안을 하나 권한다
            - 평온·무덤덤: 이유 없이 반복되는 건수를 조용히 비춰준다

            [말투]
            - 다정한 반말. 어루만지되 사실은 흐리지 않는다.
            - "~해라", "~금지다", "~절제해라" 같은 명령·훈계 어조는 쓰지 않는다.
            - 제안할 때는 "~해볼까?", "~어때?" 처럼 곁에서 권하는 어조를 쓴다.
            - 문구 하나는 두 문장 이내, 60자 이내. 문장은 반드시 끝맺는다.

            JSON 배열만 출력하고 다른 말은 절대 붙이지 마라. (마크다운 백틱 ```json 도 절대 금지)
            형식: ["감정1 문구", "감정2 문구", ...]  ← 입력 감정 개수와 같은 길이

            ---
            아래는 완성된 입출력 예시다. 규칙을 어떻게 지키는지 이 결과물을 기준으로 삼아라.

            [예시 1] 감정 3개 → 문구 3개
            입력:
            이번 달 감정 소비 전체: 403,000원 / 11건
            감정별 지출(순서 유지):
            - 스트레스: 210,000원(금액 비중 52%), 전체 11건 중 5건, 건당 평균 42,000원
            - 뿌듯함: 120,000원(금액 비중 30%), 전체 11건 중 3건, 건당 평균 40,000원
            - 무덤덤: 73,000원(금액 비중 18%), 전체 11건 중 3건, 건당 평균 24,333원
            [이번 달 전체 기준] 가장 많이 쓴 카테고리: 배달
            출력:
            ["많이 지쳤구나. 그런데 금액의 52%가 여기 몰렸어. 이번 주는 두 번만 줄여볼까?", "잘 해낸 날이었네! 다만 건당 4만 원이면 작지 않아. 보상은 2만 원 선으로 정해둘까?", "특별한 일 없이도 11건 중 3건이 여기였어. 사고 싶을 땐 하루만 미뤄볼까?"]

            [예시 2] 감정 2개 → 문구 2개. 건수가 적어도 있는 숫자로만 말한다
            입력:
            이번 달 감정 소비 전체: 88,000원 / 3건
            감정별 지출(순서 유지):
            - 설렘: 66,000원(금액 비중 75%), 전체 3건 중 2건, 건당 평균 33,000원
            - 외로움: 22,000원(금액 비중 25%), 전체 3건 중 1건, 건당 평균 22,000원
            출력:
            ["설레는 마음 반가워! 그래도 금액의 75%가 여기야. 다음엔 3만 원 선에서 골라볼까?", "혼자인 밤이었구나. 한 번에 22,000원을 썼어. 담아두고 하루 뒤에 다시 볼까?"]
            """;

    /** 모델 응답 파싱 전용. LLM의 불안정한 JSON 포맷(작은따옴표 등)을 방어하기 위해 유연한 파싱 허용. */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES)
            .enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);

    private final OpenAIClient openAIClient;
    private final RuleBasedInsightCardGenerator fallback;
    private final String model;
    private final Duration timeout;

    private final AiCallGuard guard;

    public GptInsightCardGenerator(OpenAIClient openAIClient,
                                   RuleBasedInsightCardGenerator fallback,
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
    public List<String> emotionAnalyses(List<EmotionStatDto> emotions, String topCategory, String topTimeSlotLabel) {
        if (emotions.isEmpty()) {
            return List.of();
        }

        // 비율·건당 평균은 여기서 자바로 계산해 넘긴다. 모델이 나눗셈을 하게 두면 화면 숫자와 어긋난다.
        // 이 값들이 없으면 짚어줄 사실이 없어 문구가 두루뭉술해진다(#204).
        long totalAmount = emotions.stream().mapToLong(EmotionStatDto::amount).sum();
        long totalCount = emotions.stream().mapToLong(EmotionStatDto::count).sum();

        StringBuilder input = new StringBuilder();
        input.append(String.format("이번 달 감정 소비 전체: %,d원 / %d건%n", totalAmount, totalCount));
        input.append("감정별 지출(순서 유지):\n");
        for (EmotionStatDto e : emotions) {
            long share = totalAmount > 0 ? Math.round(e.amount() * 100.0 / totalAmount) : 0;
            long perCase = e.count() > 0 ? e.amount() / e.count() : 0;
            input.append(String.format("- %s: %,d원(금액 비중 %d%%), 전체 %d건 중 %d건, 건당 평균 %,d원%n",
                    e.name(), e.amount(), share, totalCount, e.count(), perCase));
        }
        // 아래 둘은 특정 감정이 아니라 이번 달 전체 기준이다. 라벨을 붙이지 않으면
        // 모델이 개별 감정의 소비처로 오해해 "설렘일 때 쇼핑에 썼어" 같은 틀린 문장을 만든다.
        if (topCategory != null) {
            input.append("[이번 달 전체 기준] 가장 많이 쓴 카테고리: ").append(topCategory).append('\n');
        }
        if (topTimeSlotLabel != null) {
            input.append("[이번 달 전체 기준] 소비가 몰린 시간대: ").append(topTimeSlotLabel).append('\n');
        }

        try {
            List<String> parsed = parseStringArray(callModel(EMOTION_PERSONA, input.toString()));

            // 모델이 감정 하나를 빠뜨리는 일이 잦다. 예전에는 그때 3장을 통째로 폴백으로 돌려
            // 카드 세 개가 똑같은 규칙기반 문장으로 보였다(#204).
            // 앞에서부터는 순서가 어긋나지 않으므로, 받은 만큼은 그대로 쓰고
            // 모자란 뒤쪽만 규칙기반으로 채운다. 남는 건 버린다.
            if (!parsed.isEmpty()) {
                if (parsed.size() != emotions.size()) {
                    log.warn("감정 분석 개수 불일치(기대 {} / 실제 {}). 모자란 만큼만 규칙기반으로 채운다.",
                            emotions.size(), parsed.size());
                }
                List<String> ruleBased = fallback.emotionAnalyses(emotions, topCategory, topTimeSlotLabel);
                List<String> merged = new ArrayList<>(emotions.size());
                for (int i = 0; i < emotions.size(); i++) {
                    merged.add(i < parsed.size()
                            ? truncate(parsed.get(i), MAX_EMOTION_CARD_LENGTH)
                            : ruleBased.get(i));
                }
                return merged;
            }
            log.warn("감정 분석 응답이 비어 있다. 규칙기반으로 대체한다.");
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
        Response response = guard.call("감정 카드", () -> openAIClient.responses()
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


    /**
     * 길이를 넘기면 마지막 문장 끝에서 자른다.
     *
     * 글자 수로 그냥 끊으면 "필요하지 않은 소비를 절제하" 처럼 낱말 중간에서 잘려 화면에 그대로 노출된다(#204).
     * 문장 부호를 못 찾으면 어절 경계에서 자르고 말줄임표를 붙인다 — 잘렸다는 사실이라도 보이게 한다.
     */
    private String truncate(String value, int maxLength) {
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }

        String head = trimmed.substring(0, maxLength);
        int sentenceEnd = Math.max(head.lastIndexOf('.'), Math.max(head.lastIndexOf('!'), head.lastIndexOf('?')));
        if (sentenceEnd >= maxLength / 2) {
            return head.substring(0, sentenceEnd + 1).trim();
        }

        int wordEnd = head.lastIndexOf(' ');
        return (wordEnd >= maxLength / 2 ? head.substring(0, wordEnd) : head).trim() + "…";

    }
}
