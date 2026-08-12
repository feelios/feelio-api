package com.korit.feelioapi.domain.summary.service;

import com.korit.feelioapi.domain.analysis.service.SpendStatus;
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
import java.util.stream.Collectors;
import com.korit.feelioapi.global.ai.AiCallGuard;

/**
 * 말랑이 코멘트를 GPT 로 생성한다. 실패하면 null 을 반환하고 서비스가 규칙기반으로 대체한다.
 *
 * <p>수치와 상태 판정은 프롬프트에 값으로 박아 넣는다 — GPT 가 숫자를 지어내거나
 * 칭찬/경고 방향을 뒤집지 못하게 하기 위해서다.
 */
@Primary
@Component
@ConditionalOnProperty(name = "feelio.insight.provider", havingValue = "gpt")
public class GptMallangCommentGenerator implements MallangCommentGenerator {

    private static final Logger log = LoggerFactory.getLogger(GptMallangCommentGenerator.class);
    private static final int MAX_LENGTH = 60;
    private static final String SEPARATOR = "|";

    private final OpenAIClient openAIClient;
    private final String model;
    private final Duration timeout;

    private final AiCallGuard guard;

    public GptMallangCommentGenerator(OpenAIClient openAIClient,
                                      @Value("${openai.model}") String model,
                                      @Value("${openai.timeout-seconds}") long timeoutSeconds,
                                      AiCallGuard guard) {
        this.openAIClient = openAIClient;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.guard = guard;
    }

    @Override
    public MallangComment generate(SpendStatus status, long expense, long budget, int usageRate, EmotionContext emotion) {
        try {
            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(model)
                    .input(buildPrompt(status, expense, usageRate, emotion))
                    .build();
            RequestOptions options = RequestOptions.builder().timeout(timeout).build();
            Response response = guard.call("말랑이 한마디", () -> openAIClient.responses().create(params, options));

            String text = response.output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream())
                    .flatMap(content -> content.outputText().stream())
                    .map(output -> output.text())
                    .collect(Collectors.joining())
                    .trim();

            return parse(text);
        } catch (Exception e) {
            log.warn("말랑이 코멘트 생성 실패(status={}). 규칙기반으로 대체한다.", status, e);
            return null;
        }
    }

    private String buildPrompt(SpendStatus status, long expense, int usageRate, EmotionContext emotion) {
        String tone = switch (status) {
            case ZERO -> "아직 지출이 없다. 재촉하지 말고 가볍게 기록을 권해라.";
            case NO_BUDGET -> "예산을 아직 잡을 수 없다. 소진율을 언급하지 말고 지출 금액만 말해라.";
            case OVER -> "예산을 거의 다 썼다. 겁주지 말고 담백하게 짚어라.";
            case WARNING -> "예산의 절반을 넘겼다. 아직 늦지 않았다는 톤으로 짚어라.";
            case SAVING -> "예산 안에서 잘 가고 있다. 담담하게 칭찬해라.";
        };

        String numbers = status == SpendStatus.ZERO
                ? "이번 달 지출 0원."
                : status == SpendStatus.NO_BUDGET
                ? String.format("이번 달 지출 %,d원.", expense)
                : String.format("이번 달 지출 %,d원, 예산 소진율 %d%%.", expense, usageRate);

        return """
                너는 가계 기록 앱 홈 화면의 캐릭터 '말랑이'다. 사용자에게 부드러운 반말로 말한다.

                상황: %s
                수치: %s
                감정: %s

                아래 세 문장을 '|' 두 개로 이어서 한 줄로만 출력해라.
                1) 감정 공감 — 위 감정을 그대로 짚어주는 한마디. 수치는 넣지 마라.
                2) 현황 평가 — 위 수치 중 최소 하나를 그대로 문장에 넣어라. 숫자를 바꾸거나 새로 만들지 마라.
                3) 다음 행동 독려 — 구체적이고 부담 없는 제안 한 가지.

                각 문장은 %d자 이내. 훈계·과장·이모지·따옴표·번호를 쓰지 마라.
                감정을 진단하거나 고치려 들지 말고, 그 감정을 인정하는 톤으로 말해라.

                여기는 돈을 아껴 목표를 모으는 앱이다. 3번 제안은 반드시 기록·절약·목표 저금 중
                하나로 이어져야 한다. 카페·쇼핑·외식처럼 돈을 더 쓰는 행동을 권하지 마라.
                돈이 드는 활동을 언급해야 한다면 '대신 줄여보자'는 방향으로만 써라.

                예시 형식: 설렘이 가득한 달이었네.|이번 달 320,000원 썼어. 예산의 78%%야.|설레는 날 하나를 목표 저금으로 남겨볼까?
                """.formatted(tone, numbers, emotionLine(emotion), MAX_LENGTH);
    }

    /**
     * 감정 블록. 감정명·횟수·금액은 서버 집계값을 그대로 박아 넣는다 — 수치와 같은 이유로
     * GPT 가 감정을 지어내거나 8종 밖의 이름을 쓰지 못하게 한다.
     *
     * <p>기록이 없으면 감정을 언급하지 말라고 명시한다. 이 지시가 없으면 GPT 가
     * 빈 자리를 임의의 감정으로 메운다.
     */
    private String emotionLine(EmotionContext emotion) {
        if (emotion == null || !emotion.hasEmotion()) {
            return "이번 달 감정 기록이 없다. 감정을 추측하거나 언급하지 마라.";
        }

        String trend = switch (emotion.trend()) {
            case REPEATED -> " 지난달에도 같은 감정이 가장 많았다.";
            case CHANGED -> " 지난달과는 다른 감정이 올라왔다.";
            case UNKNOWN -> "";
        };

        return String.format(
                "이번 달 가장 많이 기록된 감정은 '%s'(%d번, 관련 지출 %,d원).%s 이 감정을 반드시 문장에 담아라.",
                emotion.name(), emotion.count(), emotion.amount(), trend);
    }

    /**
     * "평가|독려" 한 줄을 두 문장으로 가른다. 형식이 어긋나면 null 로 폴백에 넘긴다.
     *
     * <p>GPT 가 형식을 어길 때 폴백이 걸리는 분기라 단위 테스트 대상이다.
     * OpenAI 호출을 태우지 않고 파싱만 검증하려고 package-private 로 연다.
     */
    MallangComment parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        // limit -1 로 뒤쪽 빈 조각까지 남긴다 — "공감||독려" 같은 응답을 통과시키면
        // 빈 말풍선이 그대로 화면에 뜨므로 isUsable() 에서 걸러야 한다.
        String[] parts = text.split("\\" + SEPARATOR, -1);
        if (parts.length != 3) {
            log.warn("말랑이 코멘트 형식 불일치(문장 {}개). 규칙기반으로 대체한다.", parts.length);
            return null;
        }

        MallangComment comment = new MallangComment(
                truncate(parts[0].trim()), truncate(parts[1].trim()), truncate(parts[2].trim()));
        return comment.isUsable() ? comment : null;
    }

    private String truncate(String value) {
        return value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH);
    }
}
