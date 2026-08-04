package com.korit.feelioapi.domain.summary.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** GPT를 사용하지 않는 환경에서는 멘트만 비우고 홈의 나머지 기능은 그대로 제공한다. */
@Component
@ConditionalOnProperty(name = "feelio.insight.provider", havingValue = "rule", matchIfMissing = true)
public class EmptySummaryAiCommentGenerator implements SummaryAiCommentGenerator {
    @Override
    public String generate(int year, int month, long currentExpense, long previousExpense) {
        return null;
    }
}
