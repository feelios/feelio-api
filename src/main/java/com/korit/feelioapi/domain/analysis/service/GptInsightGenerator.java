package com.korit.feelioapi.domain.analysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.korit.feelioapi.domain.analysis.dto.CategoryStatDto;
import com.korit.feelioapi.domain.analysis.dto.EmotionStatDto;
import com.korit.feelioapi.domain.analysis.dto.InsightDto;
import com.korit.feelioapi.domain.analysis.dto.TimeSlotStatDto;
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
 * GPT 기반 인사이트 생성(계약 §9). feelio.insight.provider=gpt 일 때만 빈이 뜨고 @Primary 로 규칙기반보다 우선한다.
 * 외부 호출이므로 실패·타임아웃·형식오류는 모두 규칙기반 결과로 대체한다 — AI 장애가 분석 화면 장애가 되면 안 된다.
 */
@Component
@Primary
@ConditionalOnProperty(name = "feelio.insight.provider", havingValue = "gpt")
public class GptInsightGenerator implements InsightGenerator {

    private static final Logger log = LoggerFactory.getLogger(GptInsightGenerator.class);

    /** ai_insights.insight_type varchar(20) — 넘으면 INSERT 가 깨지므로 잘라낸다. */
    private static final int MAX_TYPE_LENGTH = 20;
    /** ai_insights.content varchar(500) */
    private static final int MAX_CONTENT_LENGTH = 500;
    /** 한 달에 보여줄 인사이트 상한. 모델이 과하게 뱉어도 이만큼만 쓴다. */
    private static final int MAX_INSIGHTS = 5;

    /** 모델 응답 파싱 전용. 컨테이너에 ObjectMapper 빈이 없어 직접 만든다(설정도 공유할 필요가 없다). */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OpenAIClient openAIClient;
    private final RuleBasedInsightGenerator fallbackGenerator;
    private final String model;
    private final Duration timeout;

    public GptInsightGenerator(OpenAIClient openAIClient,
                               RuleBasedInsightGenerator fallbackGenerator,
                               @Value("${openai.model}") String model,
                               @Value("${openai.timeout-seconds}") long timeoutSeconds) {
        this.openAIClient = openAIClient;
        this.fallbackGenerator = fallbackGenerator;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Override
    public List<InsightDto> generate(int year,
                                     int month,
                                     List<EmotionStatDto> byEmotion,
                                     List<CategoryStatDto> byCategory,
                                     List<TimeSlotStatDto> byTimeSlot) {
        // 집계가 통째로 비면 할 말이 없다. 토큰만 쓰므로 호출하지 않는다.
        if (byEmotion.isEmpty() && byCategory.isEmpty() && byTimeSlot.isEmpty()) {
            return List.of();
        }

        try {
            String rawText = callModel(buildPrompt(year, month, byEmotion, byCategory, byTimeSlot));
            List<InsightDto> parsed = parseInsights(rawText);
            if (!parsed.isEmpty()) {
                return parsed;
            }
            log.warn("GPT 응답에서 인사이트를 못 뽑았다({}-{}). 규칙기반으로 대체한다.", year, month);
        } catch (Exception e) {
            // 타임아웃·인증오류·레이트리밋 전부 여기로 떨어진다. 사용자에게는 규칙기반 문장이 나간다.
            log.warn("GPT 인사이트 생성 실패({}-{}). 규칙기반으로 대체한다.", year, month, e);
        }
        return fallbackGenerator.generate(year, month, byEmotion, byCategory, byTimeSlot);
    }

    private String callModel(String prompt) {
        ResponseCreateParams params = ResponseCreateParams.builder()
                .model(model)
                .input(prompt)
                .build();

        // 클라이언트 기본 타임아웃은 분 단위라 길다. 호출 단위로 짧게 건다.
        RequestOptions options = RequestOptions.builder()
                .timeout(timeout)
                .build();

        Response response = openAIClient.responses().create(params, options);

        return response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(outputText -> outputText.text())
                .collect(Collectors.joining());
    }

    /** 집계 수치만 넘긴다. 이름·이메일 등 개인 식별 정보는 프롬프트에 넣지 않는다. */
    private String buildPrompt(int year,
                               int month,
                               List<EmotionStatDto> byEmotion,
                               List<CategoryStatDto> byCategory,
                               List<TimeSlotStatDto> byTimeSlot) {
        StringBuilder sb = new StringBuilder();
        sb.append(year).append("년 ").append(month).append("월 소비 집계다.\n\n");

        sb.append("[감정별 지출]\n");
        if (byEmotion.isEmpty()) {
            sb.append("- 없음\n");
        } else {
            byEmotion.forEach(e -> sb.append("- ").append(e.name())
                    .append(": ").append(e.amount()).append("원, ").append(e.count()).append("건\n"));
        }

        sb.append("\n[카테고리별 지출]\n");
        if (byCategory.isEmpty()) {
            sb.append("- 없음\n");
        } else {
            byCategory.forEach(c -> sb.append("- ").append(c.name())
                    .append(": ").append(c.amount()).append("원, ").append(c.count()).append("건\n"));
        }

        sb.append("\n[시간대별 지출]\n");
        if (byTimeSlot.isEmpty()) {
            sb.append("- 없음\n");
        } else {
            byTimeSlot.forEach(t -> sb.append("- ").append(t.label())
                    .append(": ").append(t.amount()).append("원, ").append(t.count()).append("건\n"));
        }

        sb.append("\n위 집계로 소비 습관 인사이트를 최대 ").append(MAX_INSIGHTS).append("개 만들어라.\n")
                .append("긍정·부정 감정을 가리지 말고 소비가 몰린 지점을 중립적으로 짚어라. 훈계하지 마라.\n")
                .append("JSON 배열만 출력하고 다른 말은 붙이지 마라.\n")
                .append("형식: [{\"type\":\"영문대문자_식별자\",\"content\":\"한국어 한 문장\"}]\n")
                .append("type 은 ").append(MAX_TYPE_LENGTH).append("자 이내, content 는 ")
                .append(MAX_CONTENT_LENGTH).append("자 이내로 써라.");

        return sb.toString();
    }

    /** 모델이 ```json 펜스나 앞뒤 설명을 붙여도 배열 부분만 잘라 파싱한다. */
    private List<InsightDto> parseInsights(String rawText) throws Exception {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }
        int start = rawText.indexOf('[');
        int end = rawText.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return List.of();
        }

        JsonNode array = objectMapper.readTree(rawText.substring(start, end + 1));
        if (!array.isArray()) {
            return List.of();
        }

        List<InsightDto> insights = new ArrayList<>();
        for (JsonNode node : array) {
            if (insights.size() >= MAX_INSIGHTS) {
                break;
            }
            String type = node.path("type").asText("");
            String content = node.path("content").asText("");
            if (type.isBlank() || content.isBlank()) {
                continue;
            }
            insights.add(new InsightDto(truncate(type, MAX_TYPE_LENGTH),
                    truncate(content, MAX_CONTENT_LENGTH)));
        }
        return insights;
    }

    private String truncate(String value, int maxLength) {
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
