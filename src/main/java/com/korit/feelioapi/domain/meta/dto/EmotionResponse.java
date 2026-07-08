package com.korit.feelioapi.domain.meta.dto;

import com.korit.feelioapi.domain.meta.entity.Emotion;

/**
 * 감정 마스터 응답 (API-CONTRACT §5). character_key·is_active 는 노출하지 않는다.
 */
public record EmotionResponse(
        Long emotionId,
        String name,
        String color,
        int sortOrder
) {
    public static EmotionResponse of(Emotion emotion) {
        return new EmotionResponse(
                emotion.getEmotionId(),
                emotion.getName(),
                emotion.getColor(),
                emotion.getSortOrder()
        );
    }
}
