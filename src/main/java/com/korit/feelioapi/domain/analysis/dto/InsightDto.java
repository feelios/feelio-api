package com.korit.feelioapi.domain.analysis.dto;

/**
 * 인사이트 문장 (API-CONTRACT §9 analysis.insights).
 * type: 인사이트 종류(EMOTION_FOCUS/TIME_PATTERN/TOP_CATEGORY 등) / content: 사용자용 문장.
 */
public record InsightDto(
        String type,
        String content
) {
}
