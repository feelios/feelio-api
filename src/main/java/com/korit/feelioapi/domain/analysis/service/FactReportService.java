package com.korit.feelioapi.domain.analysis.service;

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

/** 예산 상태와 대표 소비 카테고리로 MZ 팩트 폭격기 한 문장을 생성한다. */
@Service
public class FactReportService {

    public static final String FALLBACK_MESSAGE = "팩트 분석을 준비 중이에요.";

    private static final Logger log = LoggerFactory.getLogger(FactReportService.class);
    private static final int MAX_LENGTH = 100;
    private static final String PERSONA = """
            너는 사용자의 소비를 냉철하고 시니컬하게 분석해주는 'MZ 팩트 폭격기'다.
            가장 많이 쓴 카테고리나 예산 상태를 꼬집어 정곡을 찌르는 팩트 폭격을 해라.
            절약 중이거나 지출이 없으면 시크하게 칭찬해라.
            문장에 지출 금액이나 예산 금액 숫자(가격)를 절대 포함하지 마라.
            반드시 '존댓말(요, 습니다)'을 사용하여 정중하면서도 타격을 주어야 한다.
            한국어 한 문장만, 따옴표·설명·이모지 없이 25자 이내로 아주 아주 짧게 출력해라.
            """;

    private final OpenAIClient openAIClient;
    private final RuleBasedInsightCardGenerator fallback;
    private final String model;
    private final Duration timeout;
    private final String provider;

    public FactReportService(OpenAIClient openAIClient,
                             RuleBasedInsightCardGenerator fallback,
                             @Value("${openai.model}") String model,
                             @Value("${openai.timeout-seconds}") long timeoutSeconds,
                             @Value("${feelio.insight.provider:rule}") String provider) {
        this.openAIClient = openAIClient;
        this.fallback = fallback;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.provider = provider;
    }

    public String generate(SpendStatus status, long expense, long budget, String topCategory) {
        if (!"gpt".equalsIgnoreCase(provider)) {
            return fallback.factReport(status, topCategory, expense);
        }

        try {
            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(model)
                    .instructions(PERSONA)
                    .input(buildInput(status, expense, budget, topCategory))
                    .build();
            Response response = openAIClient.responses().create(
                    params,
                    RequestOptions.builder().timeout(timeout).build()
            );
            String text = response.output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream())
                    .flatMap(content -> content.outputText().stream())
                    .map(output -> output.text())
                    .collect(Collectors.joining())
                    .trim();
            if (text.isBlank()) {
                return fallback.factReport(status, topCategory, expense);
            }
            String sanitized = stripQuotes(text);
            return sanitized.length() <= MAX_LENGTH ? sanitized : sanitized.substring(0, MAX_LENGTH);
        } catch (Exception e) {
            log.warn("팩트 폭격기 생성 실패. 룰 기반 폴백으로 대체한다.", e);
            return fallback.factReport(status, topCategory, expense);
        }
    }

    private String buildInput(SpendStatus status, long expense, long budget, String topCategory) {
        String state = switch (status) {
            case OVER -> "예산 위험 또는 초과";
            case WARNING -> "예산 주의 구간";
            case SAVING -> "절약 중";
            case ZERO -> "지출 0원";
            case NO_BUDGET -> "예산 미설정";
        };
        return String.format(
                "상태: %s%n이번 달 지출: %,d원%n이번 달 예산: %,d원%n가장 많이 쓴 카테고리: %s",
                state, expense, budget, topCategory == null ? "없음" : topCategory
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
