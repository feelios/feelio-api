package com.korit.feelioapi.domain.summary.service;

import org.springframework.stereotype.Component;

import java.util.List;

/** GPT 실패 시에도 실제 증감 방향과 어긋나지 않는 결정적 폴백. */
@Component
public class RuleEmotionSignalCommentGenerator implements EmotionSignalCommentGenerator {
    @Override
    public String generate(int year, int month, List<EmotionSignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return "감정 소비가 지난달과 비슷하게 이어지고 있어. 지금 흐름을 같이 살펴보자.";
        }
        EmotionSignal top = signals.get(0);
        if (top.rate() > 0) {
            return String.format("이번 달은 %s 소비가 지난달보다 %d%% 늘었어. 어떤 순간에 늘었는지 같이 들여다보자.",
                    top.name(), top.rate());
        }
        return String.format("이번 달은 %s 소비가 지난달보다 %d%% 줄었어. 달라진 흐름을 같이 살펴보자.",
                top.name(), Math.abs(top.rate()));
    }
}
