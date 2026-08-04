package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.CategoryStatDto;
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

/** 최근 7일 카테고리별 지출로 이번 주에 실천할 맞춤 챌린지 한 개를 생성한다. */
@Service
public class ChallengeService {

    public static final String FALLBACK_MESSAGE = "맞춤 챌린지를 준비 중이에요.";

    private static final Logger log = LoggerFactory.getLogger(ChallengeService.class);
    private static final int MAX_LENGTH = 80;
    private static final String PERSONA = """
            너는 과소비 패턴을 현실적인 행동으로 바꾸는 '챌린지 마스터'다.
            최근 7일 카테고리별 지출에서 가장 소비가 몰린 항목을 중심으로 이번 주에 바로 실천할 미션 하나를 제안해라.
            금지처럼 막연한 표현 대신 횟수·시간·금액 중 하나를 넣어 측정 가능하게 만들어라.
            예: "이번 주 배달은 2번까지만 주문하기", "밤 10시 이후 카페 결제하지 않기"
            사용자를 비난하거나 불가능한 목표를 제시하지 마라.
            한국어 미션 한 문장만, 번호·따옴표·설명·이모지 없이 80자 이내로 출력해라.
            """;

    private final OpenAIClient openAIClient;
    private final String model;
    private final Duration timeout;
    private final String provider;

    public ChallengeService(OpenAIClient openAIClient,
                            @Value("${openai.model}") String model,
                            @Value("${openai.timeout-seconds}") long timeoutSeconds,
                            @Value("${feelio.insight.provider:rule}") String provider) {
        this.openAIClient = openAIClient;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.provider = provider;
    }

    public String generate(List<CategoryStatDto> weeklyCategories) {
        if (weeklyCategories == null || weeklyCategories.isEmpty()
                || !"gpt".equalsIgnoreCase(provider)) {
            return FALLBACK_MESSAGE;
        }

        try {
            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(model)
                    .instructions(PERSONA)
                    .input(buildInput(weeklyCategories))
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
                return FALLBACK_MESSAGE;
            }
            String sanitized = stripQuotes(text);
            return sanitized.length() <= MAX_LENGTH ? sanitized : sanitized.substring(0, MAX_LENGTH);
        } catch (Exception e) {
            log.warn("맞춤 챌린지 생성 실패. 준비 중 문구로 대체한다.", e);
            return FALLBACK_MESSAGE;
        }
    }

    private String buildInput(List<CategoryStatDto> weeklyCategories) {
        StringBuilder input = new StringBuilder("최근 7일 카테고리별 지출(금액 내림차순):\n");
        weeklyCategories.stream().limit(5).forEach(category -> input
                .append("- ").append(category.name())
                .append(": ").append(category.amount()).append("원, ")
                .append(category.count()).append("건\n"));
        return input.toString();
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
