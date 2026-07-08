package com.korit.feelioapi.domain.analysis.dto;

/**
 * 감정별 지출 집계 (API-CONTRACT §9 analysis.byEmotion). amount 내림차순으로 반환.
 */
public record EmotionStatDto(
        Long emotionId,
        String name,
        String color,
        Long amount,
        Long count
) {
}
