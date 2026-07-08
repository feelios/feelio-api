package com.korit.feelioapi.domain.universe.dto;

/**
 * 소비가 가장 몰린 감정 (API-CONTRACT §9 universe.focusEmotion). 긍정·부정 무관.
 * 거래가 없으면 null.
 */
public record FocusEmotionDto(
        Long emotionId,
        String name,
        String color,
        Long monthlyAmount
) {
}
