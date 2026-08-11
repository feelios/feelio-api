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

    /**
     * 반복 패턴 화면(반복되는 감정소비 패턴)의 분석 문구용 페르소나.
     *
     * 이전에는 "1문장으로 짧고 뼈때리는 조언"이라 결과가 훈수 한 줄로 끝났다.
     * 화면은 횟수·감정·사용처·시간대를 이미 다 보여주고 있어서, 같은 사실을 한 번 더 말하면
     * 사용자가 얻는 게 없다. 상담사가 그 표를 보고 무엇을 읽어냈는지가 이 자리의 값어치다.
     * 그래서 관찰·해석·처방 세 문장으로 구조를 고정한다.
     */
    private static final String PATTERN_PERSONA = """
            너는 소비 심리를 다루는 재무 상담사다. 반복되는 감정-소비 조합 하나를 놓고 상담하듯 분석해라.
            정확히 세 문장으로 쓴다.
            1) 관찰: 주어진 숫자(반복 횟수·시간대)를 인용해 무엇이 반복되고 있는지 짚는다.
            2) 해석: 그 감정이 왜 이 지출로 이어지는지 가능성으로 설명한다. 단정하지 마라.
            3) 처방: 다음에 같은 순간이 왔을 때 바로 해볼 수 있는 구체적인 행동 하나를 제안한다.
            주어진 값만 쓰고 없는 수치를 지어내지 마라.
            비난·훈계·진단명은 쓰지 말고, 평가가 아니라 해석을 준다.
            머리말이나 목록기호 없이 한국어 존댓말 세 문장만 출력해라.
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
            return patternFallback(emotion, category, timeSlot, count);
        }
        try {
            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(model)
                    .instructions(PATTERN_PERSONA)
                    .input(buildPatternInput(emotion, category, timeSlot, count))
                    .build();
            Response response = guard.call("패턴 분석",
                    () -> openAIClient.responses().create(params, RequestOptions.builder().timeout(timeout).build()));
            String text = response.output().stream().flatMap(i -> i.message().stream()).flatMap(m -> m.content().stream()).flatMap(c -> c.outputText().stream()).map(o -> o.text()).collect(Collectors.joining()).trim();
            if (text.isBlank()) {
                log.warn("패턴 분석이 빈 응답이라 규칙 기반 문구로 대체한다.");
                return patternFallback(emotion, category, timeSlot, count);
            }
            return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH);
        } catch (Exception e) {
            // 예전에는 "패턴 분석 중 오류가 발생했습니다."를 그대로 돌려줬다. 이 문자열은
            // 캐시에 저장돼 다음 거래가 생길 때까지 화면에 박혀 있는다. 분석 자리에는
            // 분석이 있어야 하므로, 모델이 죽어도 규칙 기반 해석으로 채운다.
            log.warn("패턴 분석 생성 실패. 규칙 기반 문구로 대체한다.", e);
            return patternFallback(emotion, category, timeSlot, count);
        }
    }

    private String buildPatternInput(String emotion, String category, String timeSlot, int count) {
        return """
                감정: %s
                사용처: %s
                시간대: %s
                이 조합이 반복된 횟수: %d회
                """.formatted(emotion, category, timeSlot, count);
    }

    /** GPT 없이도 이 문구가 그대로 화면에 나간다. 사실 통보가 아니라 해석까지 준다. */
    private String patternFallback(String emotion, String category, String timeSlot, int count) {
        return "%s 시간대에 '%s' 감정이 들 때 '%s' 지출이 %d번 반복됐어요. ".formatted(timeSlot, emotion, category, count)
                + "그 시간의 기분을 소비로 달래는 흐름이 자리 잡았을 수 있어요. "
                + "다음에 같은 순간이 오면 결제 전에 10분만 미뤄 보세요.";
    }
}
