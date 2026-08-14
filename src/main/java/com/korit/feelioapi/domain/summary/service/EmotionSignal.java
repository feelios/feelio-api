package com.korit.feelioapi.domain.summary.service;

/** 전월 대비 감정 기록 변화. 수치 판단은 서버가 하고 AI는 문장만 만든다. */
public record EmotionSignal(
        String name,
        int rate,
        int currentCount,
        int previousCount,
        long currentAmount
) {
}
