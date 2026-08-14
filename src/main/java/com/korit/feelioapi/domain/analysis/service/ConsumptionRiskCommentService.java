package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.global.ai.AiCallGuard;
import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.stream.Collectors;

/**
 * '소비 위험도' 카드의 한 줄 코멘트를 만든다.
 *
 * <p>예전에는 {@code budget - expense} 를 등급별 말투만 바꿔 찍었다("이제 143,502원 남았어요").
 * 옆의 등급(주의)이 이미 말한 것을 금액으로 되풀이할 뿐이라, <b>왜</b> 그 등급인지는 알려주지 않았다.
 * 이 카드는 그 달의 소비를 두고 위험도를 말하는 자리이므로, 소진율·최다 소비 카테고리·지배 감정을
 * 함께 넘겨 "무엇 때문에 이 등급인지"를 한 줄로 받는다.
 *
 * <p>{@link FactReportService} 와 같은 구성이다 — 서킷 브레이커({@link AiCallGuard})를 앞에 두고,
 * 실패·타임아웃·빈 응답이면 규칙기반 문구로 넘어간다. 이 카드는 신호등 옆에 늘 떠 있어야 해서
 * 문구가 비는 상황을 만들면 안 된다.
 */
@Service
public class ConsumptionRiskCommentService {

    private static final Logger log = LoggerFactory.getLogger(ConsumptionRiskCommentService.class);

    /** 카드 한 줄에 들어가는 길이. 넘으면 화면에서 잘린다. */
    private static final int MAX_LENGTH = 40;

    private static final String PERSONA = """
            너는 사용자의 이번 달 소비를 짚어 주는 가계 코치다.
            예산 소진율·가장 많이 쓴 카테고리·그 소비를 이끈 감정을 근거로,
            지금 소비 속도가 왜 이 등급인지를 한 문장으로 알려 줘라.
            남은 금액이나 초과 금액을 그대로 읊지 마라 — 옆 칸이 이미 등급을 말하고 있다.
            감정과 카테고리를 이어서 말해야 도움이 된다.
            훈계하지 말고 담백하게, 존댓말(요)로 끝내라.
            한국어 한 문장만, 따옴표·설명·이모지 없이 30자 이내로 아주 짧게 출력해라.
            """;

    private final OpenAIClient openAIClient;
    private final RuleBasedInsightCardGenerator fallback;
    private final String model;
    private final Duration timeout;
    private final String provider;
    private final AiCallGuard guard;

    public ConsumptionRiskCommentService(OpenAIClient openAIClient,
                                         RuleBasedInsightCardGenerator fallback,
                                         @Value("${openai.model}") String model,
                                         @Value("${openai.timeout-seconds}") long timeoutSeconds,
                                         @Value("${feelio.insight.provider:rule}") String provider,
                                         AiCallGuard guard) {
        this.openAIClient = openAIClient;
        this.fallback = fallback;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.provider = provider;
        this.guard = guard;
    }

    /**
     * @param topCategory 이번 달 지출이 가장 큰 카테고리 (없으면 null)
     * @param topEmotion  이번 달 지출이 가장 큰 감정 (없으면 null)
     */
    public String generate(SpendStatus status, long expense, long budget, String topCategory, String topEmotion) {
        // 판정 불가·지출 0원은 소비를 두고 할 말 자체가 없다. 모델을 부를 이유가 없다.
        if (status == SpendStatus.NO_BUDGET || status == SpendStatus.ZERO) {
            return fallback.riskComment(status, 0, topCategory, topEmotion);
        }

        int usageRate = budget > 0 ? (int) Math.round(expense * 100.0 / budget) : 0;
        if (!"gpt".equalsIgnoreCase(provider)) {
            return fallback.riskComment(status, usageRate, topCategory, topEmotion);
        }

        try {
            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(model)
                    .instructions(PERSONA)
                    .input(buildInput(status, usageRate, topCategory, topEmotion))
                    .build();
            Response response = guard.call("소비 위험도 코멘트", () -> openAIClient.responses().create(
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
            if (text.isBlank()) {
                return fallback.riskComment(status, usageRate, topCategory, topEmotion);
            }
            String sanitized = stripQuotes(text);
            return sanitized.length() <= MAX_LENGTH ? sanitized : sanitized.substring(0, MAX_LENGTH);
        } catch (Exception e) {
            log.warn("소비 위험도 코멘트 생성 실패. 룰 기반 폴백으로 대체한다.", e);
            return fallback.riskComment(status, usageRate, topCategory, topEmotion);
        }
    }

    private String buildInput(SpendStatus status, int usageRate, String topCategory, String topEmotion) {
        String state = switch (status) {
            case OVER -> "위험(예산 90% 이상)";
            case WARNING -> "주의(예산 70~90%)";
            case SAVING -> "안전(예산 70% 미만)";
            case ZERO -> "지출 없음";
            case NO_BUDGET -> "예산 미설정";
        };
        return String.format(
                "등급: %s%n예산 소진율: %d%%%n가장 많이 쓴 카테고리: %s%n소비를 이끈 감정: %s",
                state, usageRate,
                topCategory == null ? "없음" : topCategory,
                topEmotion == null ? "없음" : topEmotion
        );
    }

    private String stripQuotes(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2
                && (trimmed.startsWith("\"") && trimmed.endsWith("\"")
                || trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}
