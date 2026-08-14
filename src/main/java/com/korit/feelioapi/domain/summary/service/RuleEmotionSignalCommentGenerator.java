package com.korit.feelioapi.domain.summary.service;

import org.springframework.stereotype.Component;

import java.util.List;

/** GPT 실패 시에도 실제 증감 방향과 어긋나지 않는 결정적 폴백. */
@Component
public class RuleEmotionSignalCommentGenerator implements EmotionSignalCommentGenerator {
    @Override
    public String generate(int year, int month, List<EmotionSignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return "감정 소비가 지난달과 비슷해요, 요즘 마음을 천천히 돌아보세요.";
        }
        EmotionSignal top = signals.get(0);
        if (top.rate() > 0) {
            return String.format("이번 달 %s 소비가 지난달보다 %d%% 늘었어요, 어떤 순간이었는지 떠올려보세요.",
                    top.name(), top.rate());
        }
        return String.format("이번 달 %s 소비가 지난달보다 %d%% 줄었어요, 달라진 마음을 천천히 돌아보세요.",
                top.name(), Math.abs(top.rate()));
    }
}
